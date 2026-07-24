package io.github.alirezajavan.downpour.internal

import io.github.alirezajavan.downpour.api.Checksum
import io.github.alirezajavan.downpour.api.ChecksumAlgorithm
import io.github.alirezajavan.downpour.api.ConflictStrategy
import io.github.alirezajavan.downpour.api.DiagnosticReport
import io.github.alirezajavan.downpour.api.DownloadDestination
import io.github.alirezajavan.downpour.api.DownloadItem
import io.github.alirezajavan.downpour.api.DownloadListener
import io.github.alirezajavan.downpour.api.DownloadManager
import io.github.alirezajavan.downpour.api.DownloadManagerConfig
import io.github.alirezajavan.downpour.api.DownloadRequest
import io.github.alirezajavan.downpour.api.DuplicatePolicy
import io.github.alirezajavan.downpour.api.GroupProgress
import io.github.alirezajavan.downpour.api.NetworkType
import io.github.alirezajavan.downpour.api.Priority
import io.github.alirezajavan.downpour.api.QueueSnapshot
import io.github.alirezajavan.downpour.api.QueueSnapshotItem
import io.github.alirezajavan.downpour.api.SerializableChecksum
import io.github.alirezajavan.downpour.api.SerializableDownloadSchedule
import io.github.alirezajavan.downpour.api.SerializableRetryPolicy
import io.github.alirezajavan.downpour.internal.data.DownloadRepository
import io.github.alirezajavan.downpour.internal.data.DownloadStatus
import io.github.alirezajavan.downpour.internal.data.db.DownloadEntity
import io.github.alirezajavan.downpour.internal.data.toDiagnosticReport
import io.github.alirezajavan.downpour.internal.data.toEntity
import io.github.alirezajavan.downpour.internal.engine.DownloadEngine
import io.github.alirezajavan.downpour.internal.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

internal class DefaultDownloadManager(
    private val repository: DownloadRepository,
    private val engine: DownloadEngine,
    private val scope: CoroutineScope,
    private val config: DownloadManagerConfig,
    private val logger: Logger,
    private val eventDispatcher: DownloadEventDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) : DownloadManager {
    override fun enqueue(request: DownloadRequest): String {
        logger.d("Enqueuing request: $request")
        val intercepted =
            config.interceptors.fold(request) { req, interceptor ->
                interceptor.intercept(req)
            }

        val policy = intercepted.duplicatePolicy ?: config.duplicatePolicy
        if (policy == DuplicatePolicy.REUSE_EXISTING) {
            val destinationPath =
                when (val dest = intercepted.destination) {
                    is DownloadDestination.File -> dest.path
                    is DownloadDestination.Uri -> dest.uriString
                }
            val existingId =
                kotlinx.coroutines.runBlocking(config.ioDispatcher) {
                    repository.findNonTerminalByUrlAndPath(intercepted.url, destinationPath)
                }
            if (!existingId.isNullOrEmpty()) {
                logger.i("Found existing non-terminal download $existingId for URL: ${intercepted.url}, reusing ID")
                return existingId
            }
        }

        val id = idProvider()
        logger.i("Enqueued download $id for URL: ${intercepted.url}")
        scope.launch {
            repository.insert(intercepted.toEntity(id, clock()))
            engine.onEnqueued()
        }
        return id
    }

    override fun enqueueAll(requests: List<DownloadRequest>): List<String> = requests.map { enqueue(it) }

    override suspend fun setPriority(
        id: String,
        priority: Priority,
    ): Unit = engine.setPriority(id, priority.ordinal)

    override suspend fun moveToFront(id: String): Unit = engine.moveToFront(id)

    override suspend fun pause(id: String): Unit = engine.pause(id)

    override suspend fun resume(id: String): Unit = engine.resume(id)

    override suspend fun cancel(id: String): Unit = engine.cancel(id)

    override suspend fun retry(id: String): Unit = engine.retry(id)

    override suspend fun remove(
        id: String,
        deleteFile: Boolean,
    ): Unit = engine.remove(id, deleteFile)

    override suspend fun pauseAll(): Unit = engine.pauseAll()

    override suspend fun resumeAll(): Unit = engine.resumeAll()

    override suspend fun cancelAll(): Unit = engine.cancelAll()

    override suspend fun pauseByTag(tag: String): Unit = engine.pauseByTag(tag)

    override suspend fun resumeByTag(tag: String): Unit = engine.resumeByTag(tag)

    override suspend fun cancelByTag(tag: String): Unit = engine.cancelByTag(tag)

    override suspend fun removeByTag(
        tag: String,
        deleteFiles: Boolean,
    ): Unit = engine.removeByTag(tag, deleteFiles)

    override suspend fun get(id: String): DownloadItem? = repository.getItem(id)

    override suspend fun getDiagnosticReport(id: String): DiagnosticReport? = repository.getEntity(id)?.toDiagnosticReport()

    override suspend fun getAll(): List<DownloadItem> = repository.getAllItems()

    override suspend fun getByTag(tag: String): List<DownloadItem> = repository.getItemsByTag(tag)

    override fun observe(id: String): Flow<DownloadItem?> = repository.observeItem(id)

    override fun observeAll(): Flow<List<DownloadItem>> = repository.observeAllItems()

    override fun observeByTag(tag: String): Flow<List<DownloadItem>> = repository.observeItemsByTag(tag)

    override fun observeGroupProgress(tag: String): Flow<GroupProgress> = repository.observeGroupProgress(tag)

    override fun addListener(listener: DownloadListener): Unit = eventDispatcher.add(listener)

    override fun removeListener(listener: DownloadListener): Unit = eventDispatcher.remove(listener)

    override suspend fun exportQueue(): String {
        val nonTerminalStatuses =
            listOf(
                DownloadStatus.QUEUED,
                DownloadStatus.RUNNING,
                DownloadStatus.PAUSED,
                DownloadStatus.WAITING_FOR_NETWORK,
                DownloadStatus.SCHEDULED,
                DownloadStatus.FAILED,
            )
        val entities = repository.entitiesByStatuses(nonTerminalStatuses)
        val items =
            entities.map { entity ->
                QueueSnapshotItem(
                    url = entity.url,
                    destinationPath = entity.destinationPath,
                    destinationType = entity.destinationType,
                    headers = entity.headers,
                    priority = Priority.entries[entity.priority].name,
                    conflictStrategy = ConflictStrategy.entries[entity.conflictStrategy].name,
                    networkType = NetworkType.entries[entity.networkType].name,
                    maxConnections = entity.maxConnections,
                    retryPolicy =
                        SerializableRetryPolicy(
                            maxRetries = entity.maxRetries,
                            initialBackoffMillis = entity.initialBackoffMillis,
                            backoffMultiplier = entity.backoffMultiplier,
                            maxBackoffMillis = entity.maxBackoffMillis,
                        ),
                    maxBytesPerSecond = entity.maxBytesPerSecond,
                    checksum =
                        entity.checksumAlgorithm?.let { algoOrdinal ->
                            entity.checksumValue?.let { valHex ->
                                SerializableChecksum(ChecksumAlgorithm.entries[algoOrdinal].name, valHex)
                            }
                        },
                    tag = entity.tag,
                    workerClass = entity.workerClass,
                    metadata = entity.metadata,
                    mirrors = entity.mirrors,
                    requiresCharging = entity.requiresCharging,
                    requiresBatteryNotLow = entity.requiresBatteryNotLow,
                    requiresStorageNotLow = entity.requiresStorageNotLow,
                    schedule =
                        SerializableDownloadSchedule(
                            startTimeMillis = entity.schedule.startTimeMillis,
                            endTimeMillis = entity.schedule.endTimeMillis,
                        ),
                )
            }
        val snapshot = QueueSnapshot(items)
        return kotlinx.serialization.json.Json
            .encodeToString(QueueSnapshot.serializer(), snapshot)
    }

    override suspend fun importQueue(
        json: String,
        conflictStrategy: ConflictStrategy,
    ): List<String> {
        val snapshot =
            kotlinx.serialization.json.Json
                .decodeFromString(QueueSnapshot.serializer(), json)
        val ids = mutableListOf<String>()
        snapshot.items.forEach { item ->
            val destination =
                if (item.destinationType == 0) {
                    DownloadDestination.File(item.destinationPath)
                } else {
                    DownloadDestination.Uri(item.destinationPath)
                }
            val request =
                DownloadRequest
                    .Builder(item.url, destination)
                    .headers(item.headers)
                    .priority(Priority.valueOf(item.priority))
                    .conflictStrategy(conflictStrategy)
                    .networkType(NetworkType.valueOf(item.networkType))
                    .maxConnections(item.maxConnections)
                    .retryPolicy(
                        io.github.alirezajavan.downpour.api.RetryPolicy(
                            maxRetries = item.retryPolicy.maxRetries,
                            initialBackoff = item.retryPolicy.initialBackoffMillis.milliseconds,
                            backoffMultiplier = item.retryPolicy.backoffMultiplier,
                            maxBackoff = item.retryPolicy.maxBackoffMillis.milliseconds,
                        ),
                    ).maxBytesPerSecond(item.maxBytesPerSecond)
                    .apply {
                        item.checksum?.let { cs ->
                            checksum(Checksum(ChecksumAlgorithm.valueOf(cs.algorithm), cs.expectedHex))
                        }
                        item.tag?.let { tag(it) }
                        item.workerClass?.let { workerClass(it) }
                        item.schedule.startTimeMillis?.let { start ->
                            schedule(start, item.schedule.endTimeMillis)
                        }
                        item.metadata.forEach { (k, v) -> metadata(k, v) }
                        item.mirrors.forEach { mirror(it) }
                        requiresCharging(item.requiresCharging)
                        requiresBatteryNotLow(item.requiresBatteryNotLow)
                        requiresStorageNotLow(item.requiresStorageNotLow)
                    }.build()
            ids.add(enqueue(request))
        }
        return ids
    }
}
