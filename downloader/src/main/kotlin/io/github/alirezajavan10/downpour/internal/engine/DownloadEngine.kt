package io.github.alirezajavan10.downpour.internal.engine

import io.github.alirezajavan10.downpour.api.DownloadDestination
import io.github.alirezajavan10.downpour.api.DownloadError
import io.github.alirezajavan10.downpour.api.DownloadManagerConfig
import io.github.alirezajavan10.downpour.api.NetworkType
import io.github.alirezajavan10.downpour.internal.data.DownloadRepository
import io.github.alirezajavan10.downpour.internal.data.DownloadStatus
import io.github.alirezajavan10.downpour.internal.data.db.DownloadEntity
import io.github.alirezajavan10.downpour.internal.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

internal class DownloadEngine(
    private val scope: CoroutineScope,
    private val repository: DownloadRepository,
    private val taskFactory: () -> DownloadTask,
    private val config: DownloadManagerConfig,
    private val serviceController: DownloadServiceController,
    private val networkMonitor: NetworkMonitor,
    private val fileStore: FileStore,
) {
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val scheduleMutex = Mutex()
    private val networkWatchStarted = AtomicBoolean(false)

    suspend fun recover() {
        startNetworkWatch()
        repository.setStatusIn(
            listOf(DownloadStatus.RUNNING, DownloadStatus.WAITING_FOR_NETWORK),
            DownloadStatus.QUEUED,
        )
        schedule()
    }

    private fun startNetworkWatch() {
        if (!networkWatchStarted.compareAndSet(false, true)) return
        scope.launch { networkMonitor.changes.drop(1).collect { schedule() } }
    }

    fun onEnqueued() {
        scope.launch { schedule() }
    }

    suspend fun pause(id: String) {
        if (activeJobs.containsKey(id)) {
            repository.setStatus(id, DownloadStatus.PAUSED)
            cancelJob(id)
        } else {
            repository.setStatus(id, DownloadStatus.PAUSED)
        }
    }

    suspend fun resume(id: String) {
        repository.setStatus(id, DownloadStatus.QUEUED)
        schedule()
    }

    suspend fun pauseByTag(tag: String) {
        val entities = repository.getByTag(tag)
        repository.setStatusByTag(tag, ACTIVE_STATUSES, DownloadStatus.PAUSED)
        entities.forEach { cancelJob(it.id) }
    }

    suspend fun resumeByTag(tag: String) {
        repository.setStatusByTag(tag, listOf(DownloadStatus.PAUSED), DownloadStatus.QUEUED)
        schedule()
    }

    suspend fun cancelByTag(tag: String) {
        val entities = repository.getByTag(tag)
        repository.setStatusByTag(tag, INTERRUPTIBLE_STATUSES, DownloadStatus.CANCELLED)
        entities.forEach { entity ->
            cancelJob(entity.id)
            discardArtifacts(entity)
        }
        schedule()
    }

    suspend fun removeByTag(
        tag: String,
        deleteFiles: Boolean,
    ) {
        val entities = repository.getByTag(tag)
        entities.forEach { entity ->
            cancelJob(entity.id)
            if (deleteFiles) fileStore.delete(entity.toDestination())
        }
        repository.deleteByTag(tag)
        schedule()
    }

    suspend fun retry(id: String) {
        repository.setStatus(id, DownloadStatus.QUEUED)
        schedule()
    }

    suspend fun cancel(id: String) {
        val entity = repository.getEntity(id) ?: return
        repository.setStatus(id, DownloadStatus.CANCELLED)
        cancelJob(id)
        discardArtifacts(entity)
        schedule()
    }

    suspend fun pauseAll() {
        repository.setStatusIn(ACTIVE_STATUSES, DownloadStatus.PAUSED)
        cancelActiveJobs()
    }

    suspend fun resumeAll() {
        repository.setStatusIn(listOf(DownloadStatus.PAUSED), DownloadStatus.QUEUED)
        schedule()
    }

    suspend fun cancelAll() {
        repository.setStatusIn(INTERRUPTIBLE_STATUSES, DownloadStatus.CANCELLED)
        cancelActiveJobs()
        schedule()
    }

    private fun cancelActiveJobs() {
        activeJobs.keys.toList().forEach { cancelJob(it) }
    }

    suspend fun remove(
        id: String,
        deleteFile: Boolean,
    ) {
        cancelJob(id)
        val entity = repository.getEntity(id)
        if (deleteFile) entity?.let { fileStore.delete(it.toDestination()) }
        repository.delete(id)
        schedule()
    }

    private fun cancelJob(id: String) {
        activeJobs.remove(id)?.cancel()
    }

    private suspend fun discardArtifacts(entity: DownloadEntity) {
        fileStore.delete(entity.toDestination())
        repository.clearParts(entity.id)
    }

    private fun DownloadEntity.toDestination(): DownloadDestination =
        if (destinationType == 0) {
            DownloadDestination.File(destinationPath)
        } else {
            DownloadDestination.Uri(destinationPath)
        }

    private suspend fun schedule() =
        scheduleMutex.withLock {
            val status = networkMonitor.snapshot()

            activeJobs.forEach { (id, _) ->
                val entity = repository.getEntity(id) ?: return@forEach
                if (!status.satisfies(NetworkType.entries[entity.networkType])) {
                    repository.setStatus(id, DownloadStatus.WAITING_FOR_NETWORK)
                    cancelJob(id)
                }
            }

            val freeSlots = config.maxConcurrentDownloads - activeJobs.size
            if (freeSlots > 0) startEligible(freeSlots)
            serviceController.onActiveCountChanged(activeJobs.size)
        }

    private suspend fun startEligible(slots: Int) {
        val status = networkMonitor.snapshot()
        repository
            .nextQueued(QUEUE_SCAN_LIMIT)
            .asSequence()
            .filterNot { activeJobs.containsKey(it.id) }
            .filter { status.satisfies(NetworkType.entries[it.networkType]) }
            .take(slots)
            .forEach { start(it) }
    }

    private suspend fun start(entity: DownloadEntity) {
        repository.setStatus(entity.id, DownloadStatus.RUNNING)
        val job = scope.launch { runTask(entity) }
        activeJobs[entity.id] = job
        job.invokeOnCompletion { onJobFinished(entity.id) }
    }

    private suspend fun runTask(entity: DownloadEntity) {
        when (val result = taskFactory().run(entity)) {
            is TaskResult.Completed -> onCompleted(entity, result)
            is TaskResult.Failed -> onFailed(entity, result.error)
        }
    }

    private suspend fun onCompleted(
        entity: DownloadEntity,
        result: TaskResult.Completed,
    ) {
        repository.setProgress(
            id = entity.id,
            downloaded = result.totalBytes,
            total = result.totalBytes,
            speed = 0,
            eta = 0,
        )
        repository.setStatus(entity.id, DownloadStatus.COMPLETED)
        repository.clearParts(entity.id)

        entity.workerClass?.let { className ->
            val item = repository.getItem(entity.id) ?: return@let
            config.workerFactory.create(className)?.process(item)
        }
    }

    private suspend fun onFailed(
        entity: DownloadEntity,
        error: DownloadError,
    ) {
        val attempt = entity.retryCount
        if (error.isRetryable && attempt < entity.maxRetries) {
            scheduleRetry(entity, error, attempt + 1)
        } else {
            repository.setError(entity.id, error, attempt)
        }
    }

    private fun scheduleRetry(
        entity: DownloadEntity,
        error: DownloadError,
        nextAttempt: Int,
    ) {
        scope.launch {
            repository.setError(entity.id, error, nextAttempt)
            delay(backoffMillis(entity, nextAttempt).milliseconds)
            repository.setStatus(entity.id, DownloadStatus.QUEUED)
            schedule()
        }
    }

    private fun backoffMillis(
        entity: DownloadEntity,
        attempt: Int,
    ): Long {
        var backoff = entity.initialBackoffMillis.toDouble()
        repeat(attempt) { backoff *= entity.backoffMultiplier }
        return backoff.toLong().coerceAtMost(entity.maxBackoffMillis)
    }

    private fun onJobFinished(id: String) {
        activeJobs.remove(id)
        scope.launch { schedule() }
    }

    private companion object {
        const val QUEUE_SCAN_LIMIT = 256
        val ACTIVE_STATUSES =
            listOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.WAITING_FOR_NETWORK)
        val INTERRUPTIBLE_STATUSES =
            listOf(
                DownloadStatus.QUEUED,
                DownloadStatus.RUNNING,
                DownloadStatus.PAUSED,
                DownloadStatus.WAITING_FOR_NETWORK,
            )
    }
}
