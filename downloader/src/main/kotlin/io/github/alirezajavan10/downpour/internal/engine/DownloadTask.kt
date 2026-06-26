package io.github.alirezajavan10.downpour.internal.engine

import io.github.alirezajavan10.downpour.api.Checksum
import io.github.alirezajavan10.downpour.api.ChecksumAlgorithm
import io.github.alirezajavan10.downpour.api.ConflictStrategy
import io.github.alirezajavan10.downpour.api.DownloadDestination
import io.github.alirezajavan10.downpour.api.DownloadError
import io.github.alirezajavan10.downpour.api.DownloadManagerConfig
import io.github.alirezajavan10.downpour.internal.data.DownloadRepository
import io.github.alirezajavan10.downpour.internal.data.db.DownloadEntity
import io.github.alirezajavan10.downpour.internal.network.HttpDownloadDataSource
import io.github.alirezajavan10.downpour.internal.network.RemoteFileInfo
import io.github.alirezajavan10.downpour.internal.util.FilenameResolver
import io.github.alirezajavan10.downpour.internal.util.SpeedMeter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
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
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(entity: DownloadEntity): TaskResult =
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
        val initialDestination = entity.toDestination()
        val info = probe(entity)
        val resolvedDestination = resolveDestination(entity, initialDestination, info)
        val currentDestination = handleConflict(entity, resolvedDestination)
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

        ensureSpace(currentDestination, plan)
        if (plan.isMultiConnection) fileStore.preallocate(currentDestination, plan.totalBytes)
        execute(currentEntity, plan)
        verifyChecksum(currentEntity, currentDestination)
        val total = if (plan.hasKnownSize) plan.totalBytes else fileStore.lengthOf(currentDestination)
        return TaskResult.Completed(total)
    }

    private suspend fun resolveDestination(
        entity: DownloadEntity,
        destination: DownloadDestination,
        info: RemoteFileInfo,
    ): DownloadDestination {
        if (entity.downloadedBytes > 0) return destination
        if (destination is DownloadDestination.File) {
            val file = File(destination.path)
            if (file.isDirectory || !file.name.contains('.')) {
                val resolvedName = FilenameResolver.resolve(entity.url, info)
                val newFile =
                    if (file.isDirectory) {
                        File(file, resolvedName)
                    } else {
                        File(file.parentFile, resolvedName)
                    }
                if (newFile.absolutePath != destination.path) {
                    repository.setDestinationPath(entity.id, newFile.absolutePath)
                    return DownloadDestination.File(newFile.absolutePath)
                }
            }
        }
        return destination
    }

    private suspend fun handleConflict(
        entity: DownloadEntity,
        destination: DownloadDestination,
    ): DownloadDestination {
        if (entity.downloadedBytes > 0) return destination
        if (destination is DownloadDestination.File) {
            val file = File(destination.path)
            if (file.exists()) {
                return when (ConflictStrategy.entries[entity.conflictStrategy]) {
                    ConflictStrategy.OVERWRITE -> {
                        destination
                    }

                    ConflictStrategy.FAIL -> {
                        throw DownloadError.FileAlreadyExists(file.absolutePath)
                    }

                    ConflictStrategy.RENAME -> {
                        val uniqueFile = generateUniqueFile(file)
                        repository.setDestinationPath(entity.id, uniqueFile.absolutePath)
                        DownloadDestination.File(uniqueFile.absolutePath)
                    }
                }
            }
        }
        return destination
    }

    private fun generateUniqueFile(file: File): File {
        val parent = file.parentFile
        val name = file.nameWithoutExtension
        val extension = file.extension
        var count = 1
        var uniqueFile = file
        while (uniqueFile.exists()) {
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

    private suspend fun probe(entity: DownloadEntity): RemoteFileInfo {
        val info = dataSource.probe(entity.url, entity.headers)
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
        if (!plan.hasKnownSize) return
        val required = (plan.totalBytes - plan.alreadyDownloaded).coerceAtLeast(0)
        val usable = fileStore.usableSpaceFor(destination)
        if (usable < required) throw DownloadError.InsufficientStorage(required, usable)
    }

    private suspend fun execute(
        entity: DownloadEntity,
        plan: DownloadPlan,
    ) {
        val transfer = ActiveTransfer(entity, plan, rateLimitersFor(entity))
        try {
            runParts(transfer)
        } finally {
            withContext(NonCancellable) { persistOffsets(transfer) }
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
                url = transfer.entity.url,
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
            flushProgress(transfer, meter)
            persistOffsets(transfer)
        }
    }

    private suspend fun flushProgress(
        transfer: ActiveTransfer,
        meter: SpeedMeter,
    ) {
        val downloaded = transfer.progress.get()
        val reading = meter.sample(downloaded, transfer.plan.totalBytes)
        repository.setProgress(
            id = transfer.entity.id,
            downloaded = downloaded,
            total = transfer.plan.totalBytes,
            speed = reading.bytesPerSecond,
            eta = reading.etaMillis,
        )
    }

    private suspend fun persistOffsets(transfer: ActiveTransfer) {
        if (transfer.plan.isMultiConnection) {
            transfer.offsets.forEach { (partId, offset) ->
                repository.setPartOffset(partId, offset.get())
            }
        }
        repository.setProgress(
            id = transfer.entity.id,
            downloaded = transfer.progress.get(),
            total = transfer.plan.totalBytes,
            speed = 0,
            eta = 0,
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
        // Dynamic concurrency placeholder - currently returns entity max
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
    ) {
        val progress = AtomicLong(plan.alreadyDownloaded)
        val offsets: Map<Long, AtomicLong> =
            plan.parts.associate { it.partId to AtomicLong(it.nextByte) }

        fun offsetOf(part: PartPlan): AtomicLong = offsets.getValue(part.partId)
    }
}
