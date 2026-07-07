package io.github.alirezajavan.downpour.internal.engine

import io.github.alirezajavan.downpour.api.Checksum
import io.github.alirezajavan.downpour.api.ChecksumAlgorithm
import io.github.alirezajavan.downpour.api.ConflictStrategy
import io.github.alirezajavan.downpour.api.DownloadDestination
import io.github.alirezajavan.downpour.api.DownloadError
import io.github.alirezajavan.downpour.api.DownloadManagerConfig
import io.github.alirezajavan.downpour.api.RemoteFileMetadata
import io.github.alirezajavan.downpour.internal.data.DownloadRepository
import io.github.alirezajavan.downpour.internal.data.db.DownloadEntity
import io.github.alirezajavan.downpour.internal.network.HttpDownloadDataSource
import io.github.alirezajavan.downpour.internal.network.RemoteFileInfo
import io.github.alirezajavan.downpour.internal.util.Logger
import io.github.alirezajavan.downpour.internal.util.SpeedMeter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

internal class DownloadTask(
    private val dataSource: HttpDownloadDataSource,
    private val planner: DownloadPlanner,
    private val partDownloader: PartDownloader,
    private val repository: DownloadRepository,
    private val config: DownloadManagerConfig,
    private val globalRateLimiter: RateLimiter,
    private val ioDispatcher: CoroutineDispatcher,
    private val fileStore: FileStore,
    private val logger: Logger,
    // Shared across all task instances: serializes destination resolution so two concurrent
    // downloads (e.g. the same URL enqueued twice) deterministically claim distinct filenames.
    private val destinationMutex: Mutex,
    private val clock: () -> Long = System::currentTimeMillis,
) : DownloadTaskRunner {
    override suspend fun run(entity: DownloadEntity): TaskResult =
        withContext(ioDispatcher) {
            runCatchingDownload(entity)
        }

    private suspend fun runCatchingDownload(entity: DownloadEntity): TaskResult =
        try {
            download(entity)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: DownloadError) {
            TaskResult.Failed(error)
        } catch (unexpected: Throwable) {
            TaskResult.Failed(DownloadError.Unknown(unexpected))
        }

    private suspend fun download(entity: DownloadEntity): TaskResult {
        logger.d("Executing task for ${entity.id}")
        val activeUrl = selectUrl(entity)
        val initialDestination = entity.toDestination()
        val info = probe(entity, activeUrl)
        val currentDestination = resolveFinalDestination(entity, initialDestination, info)
        val currentEntity =
            if (currentDestination != initialDestination) {
                entity.copy(
                    destinationPath = currentDestination.toPath(),
                    destinationType = currentDestination.toType(),
                )
            } else {
                entity
            }

        fileStore.ensureParentExists(currentDestination)

        val activeConnections = calculateDynamicConnections(entity)
        val plan = planner.plan(currentEntity, info, fileStore.lengthOf(currentDestination), activeConnections)
        logger.d("Download plan for ${entity.id}: $plan")

        ensureSpace(currentDestination, plan)
        if (plan.isMultiConnection) fileStore.preallocate(currentDestination, plan.totalBytes)
        execute(currentEntity, plan, activeUrl)
        verifyChecksum(currentEntity, currentDestination)
        val total = if (plan.hasKnownSize) plan.totalBytes else fileStore.lengthOf(currentDestination)
        return TaskResult.Completed(total)
    }

    /**
     * Resolves the final on-disk destination and persists it, atomically across concurrent tasks.
     *
     * Held under [destinationMutex] so two downloads racing for the same path (the classic "same URL
     * enqueued twice" case) are serialized: each one sees the other's already-claimed path — via the
     * DB ([DownloadRepository.isDestinationClaimedByOther]) even before any file exists on disk — and
     * the RENAME strategy gives them distinct names ("file", "file (1)", …). The chosen path is
     * written back to the row immediately so it becomes the claim the next task observes.
     */
    private suspend fun resolveFinalDestination(
        entity: DownloadEntity,
        destination: DownloadDestination,
        info: RemoteFileInfo,
    ): DownloadDestination {
        // Already finalized on a previous run (resuming, or restarted before any bytes): keep it.
        // Without this guard a restart would see its own preallocated file and rename again.
        if (entity.destinationResolved || entity.downloadedBytes > 0) return destination
        if (destination !is DownloadDestination.File) return destination

        return destinationMutex.withLock {
            val named = applyResolvedFilename(entity, destination, info)
            claimDestination(entity, named)
        }
    }

    /** Expands a directory / extension-less target into a concrete filename (no DB write). */
    private fun applyResolvedFilename(
        entity: DownloadEntity,
        destination: DownloadDestination.File,
        info: RemoteFileInfo,
    ): DownloadDestination.File {
        val file = File(destination.path)
        if (!file.isDirectory && file.name.contains('.')) return destination
        val resolvedName =
            config.filenameResolver.resolve(
                RemoteFileMetadata(entity.url, info.contentDisposition, info.contentType),
            )
        val newFile = if (file.isDirectory) File(file, resolvedName) else File(file.parentFile, resolvedName)
        return DownloadDestination.File(newFile.absolutePath)
    }

    private suspend fun claimDestination(
        entity: DownloadEntity,
        destination: DownloadDestination.File,
    ): DownloadDestination {
        val file = File(destination.path)
        val taken = file.exists() || repository.isDestinationClaimedByOther(destination.path, entity.id)
        val finalDestination =
            if (!taken) {
                destination
            } else {
                when (ConflictStrategy.entries[entity.conflictStrategy]) {
                    ConflictStrategy.OVERWRITE -> destination
                    ConflictStrategy.FAIL -> throw DownloadError.FileAlreadyExists(file.absolutePath)
                    ConflictStrategy.RENAME -> DownloadDestination.File(generateUniqueFile(entity, file).absolutePath)
                }
            }
        // Persist the resolved path AND mark it resolved, so it becomes this row's claim for any
        // concurrent resolver and is never recomputed (and thus never re-renamed) on a restart.
        repository.markDestinationResolved(entity.id, finalDestination.path)
        return finalDestination
    }

    private suspend fun generateUniqueFile(
        entity: DownloadEntity,
        file: File,
    ): File {
        val parent = file.parentFile
        val name = file.nameWithoutExtension
        val extension = file.extension
        var count = 1
        var uniqueFile = file
        while (uniqueFile.exists() || repository.isDestinationClaimedByOther(uniqueFile.absolutePath, entity.id)) {
            val suffix = "($count)"
            val newName = if (extension.isEmpty()) "$name$suffix" else "$name$suffix.$extension"
            uniqueFile = File(parent, newName)
            count++
        }
        return uniqueFile
    }

    private fun verifyChecksum(
        entity: DownloadEntity,
        destination: DownloadDestination,
    ) {
        val algorithmOrdinal = entity.checksumAlgorithm ?: return
        val expected = entity.checksumValue ?: return
        val checksum = Checksum(ChecksumAlgorithm.entries[algorithmOrdinal], expected)
        ChecksumVerifier.verify(fileStore, destination, checksum)
    }

    // The active URL rotates through [primary] + mirrors by attempt number, so each retry can fail
    // over to a different source (e.g. a CDN mirror) without losing already-downloaded bytes.
    private fun selectUrl(entity: DownloadEntity): String {
        if (entity.mirrors.isEmpty()) return entity.url
        val urls = listOf(entity.url) + entity.mirrors
        return urls[entity.retryCount % urls.size]
    }

    private suspend fun probe(
        entity: DownloadEntity,
        url: String,
    ): RemoteFileInfo {
        val info = dataSource.probe(url, entity.headers)
        repository.setResumeMetadata(
            id = entity.id,
            supportsResume = info.acceptsRanges,
            total = info.totalBytes,
            etag = info.etag,
            lastModified = info.lastModified,
        )
        return info
    }

    private fun ensureSpace(
        destination: DownloadDestination,
        plan: DownloadPlan,
    ) {
        val usable = fileStore.usableSpaceFor(destination)
        if (usable < MIN_SAFE_STORAGE_BYTES) {
            throw DownloadError.InsufficientStorage(MIN_SAFE_STORAGE_BYTES, usable)
        }

        if (!plan.hasKnownSize) return
        val required = (plan.totalBytes - plan.alreadyDownloaded).coerceAtLeast(0)
        if (usable < required + MIN_SAFE_STORAGE_BYTES) {
            throw DownloadError.InsufficientStorage(required + MIN_SAFE_STORAGE_BYTES, usable)
        }
    }

    private suspend fun execute(
        entity: DownloadEntity,
        plan: DownloadPlan,
        url: String,
    ) {
        val transfer = ActiveTransfer(entity, plan, rateLimitersFor(entity), url)
        try {
            runParts(transfer)
        } finally {
            withContext(NonCancellable) { persistProgress(transfer) }
        }
        verifyCompleteness(transfer)
    }

    private fun rateLimitersFor(entity: DownloadEntity): List<RateLimiter> =
        listOf(globalRateLimiter, RateLimiter(entity.maxBytesPerSecond))
            .filterNot { it.isUnlimited }

    private suspend fun runParts(transfer: ActiveTransfer) =
        coroutineScope {
            val reporter = launch { reportProgress(transfer) }
            transfer.plan.parts
                .filterNot { it.isComplete() }
                .map { part -> launch { downloadPart(transfer, part) } }
                .joinAll()
            reporter.cancel()
        }

    private suspend fun downloadPart(
        transfer: ActiveTransfer,
        part: PartPlan,
    ) {
        val context =
            PartContext(
                url = transfer.url,
                headers = transfer.entity.headers,
                part = part,
                ifRange = transfer.plan.ifRange,
                destination = transfer.entity.toDestination(),
                isMultiConnection = transfer.plan.isMultiConnection,
                progress = transfer.progress,
                partOffset = transfer.offsetOf(part),
                rateLimiters = transfer.rateLimiters,
            )
        partDownloader.download(context)
    }

    private suspend fun reportProgress(transfer: ActiveTransfer) {
        val meter = SpeedMeter(clock = clock)
        while (true) {
            delay(config.progressUpdateInterval)
            val downloaded = transfer.progress.get()
            val reading = meter.sample(downloaded, transfer.plan.totalBytes)
            persistProgress(transfer, reading.bytesPerSecond, reading.etaMillis)
        }
    }

    private suspend fun persistProgress(
        transfer: ActiveTransfer,
        speed: Long = 0,
        eta: Long = 0,
    ) {
        if (transfer.plan.isMultiConnection) {
            transfer.offsets.forEach { (partId, offset) ->
                repository.setPartOffset(partId, offset.get())
            }
        }
        // Gated on RUNNING: once the engine has paused/cancelled/completed the row this is a no-op,
        // so a late or in-flight flush can never make a paused download's progress keep climbing.
        repository.setRunningProgress(
            id = transfer.entity.id,
            downloaded = transfer.progress.get(),
            total = transfer.plan.totalBytes,
            speed = speed,
            eta = eta,
        )
    }

    private fun verifyCompleteness(transfer: ActiveTransfer) {
        val total = transfer.plan.totalBytes
        if (transfer.plan.hasKnownSize && transfer.progress.get() < total) {
            throw DownloadError.ContentValidation(
                "Incomplete transfer: ${transfer.progress.get()} of $total bytes",
            )
        }
    }

    private fun calculateDynamicConnections(entity: DownloadEntity): Int {
        if (config.adaptiveConcurrency && entity.effectiveConnections > 0) {
            return entity.effectiveConnections
        }
        return entity.maxConnections
    }

    private fun DownloadEntity.toDestination(): DownloadDestination =
        when (destinationType) {
            0 -> DownloadDestination.File(destinationPath)
            else -> DownloadDestination.Uri(destinationPath)
        }

    private fun DownloadDestination.toPath(): String =
        when (this) {
            is DownloadDestination.File -> path
            is DownloadDestination.Uri -> uriString
        }

    private fun DownloadDestination.toType(): Int =
        when (this) {
            is DownloadDestination.File -> 0
            is DownloadDestination.Uri -> 1
        }

    private fun PartPlan.isComplete(): Boolean = !isOpenEnded && nextByte > end

    private class ActiveTransfer(
        val entity: DownloadEntity,
        val plan: DownloadPlan,
        val rateLimiters: List<RateLimiter>,
        val url: String,
    ) {
        val progress = AtomicLong(plan.alreadyDownloaded)
        val offsets: Map<Long, AtomicLong> =
            plan.parts.associate { it.partId to AtomicLong(it.nextByte) }

        fun offsetOf(part: PartPlan): AtomicLong = offsets.getValue(part.partId)
    }

    private companion object {
        const val MIN_SAFE_STORAGE_BYTES = 100L * 1024 * 1024
    }
}
