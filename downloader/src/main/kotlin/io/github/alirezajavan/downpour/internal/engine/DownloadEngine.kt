package io.github.alirezajavan.downpour.internal.engine

import io.github.alirezajavan.downpour.api.DownloadDestination
import io.github.alirezajavan.downpour.api.DownloadError
import io.github.alirezajavan.downpour.api.DownloadManagerConfig
import io.github.alirezajavan.downpour.api.NetworkType
import io.github.alirezajavan.downpour.internal.data.DownloadRepository
import io.github.alirezajavan.downpour.internal.data.DownloadStatus
import io.github.alirezajavan.downpour.internal.data.db.DownloadEntity
import io.github.alirezajavan.downpour.internal.network.NetworkMonitor
import io.github.alirezajavan.downpour.internal.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
    private val taskFactory: () -> DownloadTaskRunner,
    private val config: DownloadManagerConfig,
    private val serviceController: DownloadServiceController,
    private val networkMonitor: NetworkMonitor,
    private val fileStore: FileStore,
    private val logger: Logger,
) {
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val retryJobs = ConcurrentHashMap<String, Job>()

    /**
     * Serializes every state transition. All status writes plus (de)registration of running jobs
     * and retry timers happen under this lock, so the user-facing operations (pause/resume/cancel/
     * retry/remove and their bulk/tag variants) can never interleave with the scheduler's
     * [start]/[schedule]. This is what makes "I paused but it kept downloading" impossible: a pause
     * either runs fully before a start (and the start then sees the row is no longer eligible) or
     * fully after it (and it finds the job in [activeJobs] and cancels it).
     *
     * The mutex is NOT reentrant. Helpers that mutate state assume the lock is already held; public
     * entry points take the lock for their critical section, release it, then call [schedule].
     */
    private val scheduleMutex = Mutex()
    private val networkWatchStarted = AtomicBoolean(false)
    private val isRecovered = AtomicBoolean(false)

    suspend fun recover() {
        if (isRecovered.getAndSet(true)) return

        scheduleMutex.withLock {
            logger.d("Recovering leaked downloads...")
            startNetworkWatch()
            val activeIds = activeJobs.keys.toList()
            repository.setStatusIn(
                from = listOf(DownloadStatus.RUNNING, DownloadStatus.WAITING_FOR_NETWORK),
                to = DownloadStatus.QUEUED,
                excludeIds = activeIds,
            )
        }
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
        scheduleMutex.withLock {
            cancelRetry(id)
            repository.setStatus(id, DownloadStatus.PAUSED)
            cancelJob(id)
        }
        schedule()
    }

    suspend fun resume(id: String) {
        logger.d("Resuming download: $id")
        scheduleMutex.withLock {
            cancelRetry(id)
            repository.setStatus(id, DownloadStatus.QUEUED)
        }
        schedule()
    }

    suspend fun pauseByTag(tag: String) {
        scheduleMutex.withLock {
            val entities = repository.getByTag(tag)
            repository.setStatusByTag(tag, ACTIVE_STATUSES, DownloadStatus.PAUSED)
            entities.forEach {
                cancelRetry(it.id)
                cancelJob(it.id)
            }
        }
        schedule()
    }

    suspend fun resumeByTag(tag: String) {
        scheduleMutex.withLock {
            repository.getByTag(tag).forEach { cancelRetry(it.id) }
            repository.setStatusByTag(tag, listOf(DownloadStatus.PAUSED), DownloadStatus.QUEUED)
        }
        schedule()
    }

    suspend fun cancelByTag(tag: String) {
        scheduleMutex.withLock {
            val entities = repository.getByTag(tag)
            repository.setStatusByTag(tag, INTERRUPTIBLE_STATUSES, DownloadStatus.CANCELLED)
            entities.forEach { entity ->
                cancelRetry(entity.id)
                cancelJob(entity.id)
                discardArtifacts(entity)
            }
        }
        schedule()
    }

    suspend fun removeByTag(
        tag: String,
        deleteFiles: Boolean,
    ) {
        scheduleMutex.withLock {
            val entities = repository.getByTag(tag)
            entities.forEach { entity ->
                cancelRetry(entity.id)
                cancelJob(entity.id)
                if (deleteFiles) fileStore.delete(entity.toDestination())
            }
            repository.deleteByTag(tag)
        }
        schedule()
    }

    suspend fun retry(id: String) {
        scheduleMutex.withLock {
            cancelRetry(id)
            repository.setStatus(id, DownloadStatus.QUEUED)
        }
        schedule()
    }

    suspend fun cancel(id: String) {
        scheduleMutex.withLock {
            val entity = repository.getEntity(id) ?: return@withLock
            cancelRetry(id)
            repository.setStatus(id, DownloadStatus.CANCELLED)
            cancelJob(id)
            discardArtifacts(entity)
        }
        schedule()
    }

    suspend fun pauseAll() {
        scheduleMutex.withLock {
            cancelAllRetries()
            repository.setStatusIn(ACTIVE_STATUSES, DownloadStatus.PAUSED)
            cancelActiveJobs()
        }
        schedule()
    }

    suspend fun resumeAll() {
        scheduleMutex.withLock {
            cancelAllRetries()
            repository.setStatusIn(listOf(DownloadStatus.PAUSED), DownloadStatus.QUEUED)
        }
        schedule()
    }

    suspend fun cancelAll() {
        scheduleMutex.withLock {
            cancelAllRetries()
            // Snapshot the rows about to be cancelled so their on-disk artifacts can be discarded.
            val affected = repository.entitiesByStatuses(INTERRUPTIBLE_STATUSES)
            repository.setStatusIn(INTERRUPTIBLE_STATUSES, DownloadStatus.CANCELLED)
            cancelActiveJobs()
            affected.forEach { discardArtifacts(it) }
        }
        schedule()
    }

    suspend fun remove(
        id: String,
        deleteFile: Boolean,
    ) {
        scheduleMutex.withLock {
            cancelRetry(id)
            cancelJob(id)
            val entity = repository.getEntity(id)
            if (deleteFile) entity?.let { fileStore.delete(it.toDestination()) }
            repository.delete(id)
        }
        schedule()
    }

    private fun cancelActiveJobs() {
        activeJobs.keys.toList().forEach { cancelJob(it) }
    }

    /**
     * Cancels the running job for [id]. Must hold [scheduleMutex].
     *
     * Deliberately does NOT join the job. Joining here would deadlock — the task's completion path
     * ([onCompleted]/[onFailed]) re-acquires [scheduleMutex] — and would also stall every pause on
     * the in-flight socket read. Correctness instead comes from two guarantees that hold regardless
     * of when the cancelled task finally unwinds:
     *  1. The caller has already written the new status (PAUSED/CANCELLED) under this lock, and the
     *     task only writes progress via [DownloadRepository.setRunningProgress], a no-op once the row
     *     is not RUNNING — so it can never advance a paused/cancelled row.
     *  2. [onCompleted]/[onFailed] re-check the row is still RUNNING before doing anything, so a task
     *     that finishes just after cancellation cannot resurrect a terminal state.
     */
    private fun cancelJob(id: String) {
        activeJobs.remove(id)?.cancel()
    }

    private fun cancelRetry(id: String) {
        retryJobs.remove(id)?.cancel()
    }

    private fun cancelAllRetries() {
        retryJobs.keys.toList().forEach { cancelRetry(it) }
    }

    private suspend fun discardArtifacts(entity: DownloadEntity) {
        fileStore.delete(entity.toDestination())
        repository.clearParts(entity.id)
        repository.setProgress(
            id = entity.id,
            downloaded = 0,
            total = entity.totalBytes,
            speed = 0,
            eta = 0,
        )
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
            logger.d("Scheduling downloads. Network status: $status")

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

    /**
     * Starts a download. Must hold [scheduleMutex] (only [startEligible] calls it).
     *
     * The job is created lazily and registered in [activeJobs] BEFORE the RUNNING status is written,
     * so there is no window where the row is RUNNING but unknown to pause/cancel. Because everything
     * runs under the mutex, a pause/cancel arriving "at the same time" is fully ordered against this.
     */
    private suspend fun start(entity: DownloadEntity) {
        logger.i("Starting download: ${entity.id} (${entity.url})")
        val job = scope.launch(start = CoroutineStart.LAZY) { runTask(entity) }
        activeJobs[entity.id] = job
        job.invokeOnCompletion { onJobFinished(entity.id, job) }
        repository.setStatus(entity.id, DownloadStatus.RUNNING)
        job.start()
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
        val finalized =
            scheduleMutex.withLock {
                // Only finalize as COMPLETED if the row is still RUNNING. A concurrent pause/cancel
                // wins and must not be overwritten by a task that happened to finish right after.
                if (repository.getEntity(entity.id)?.status != DownloadStatus.RUNNING) {
                    return@withLock false
                }
                repository.setProgress(
                    id = entity.id,
                    downloaded = result.totalBytes,
                    total = result.totalBytes,
                    speed = 0,
                    eta = 0,
                )
                repository.setStatus(entity.id, DownloadStatus.COMPLETED)
                repository.clearParts(entity.id)
                true
            }
        if (!finalized) return
        logger.i("Download completed: ${entity.id}")

        entity.workerClass?.let { className ->
            val item = repository.getItem(entity.id) ?: return@let
            config.workerFactory.create(className)?.process(item)
        }
    }

    private suspend fun onFailed(
        entity: DownloadEntity,
        error: DownloadError,
    ) {
        logger.e("Download failed: ${entity.id}. Error: ${error.message}", error)
        scheduleMutex.withLock {
            // If the row is no longer RUNNING, the user paused/cancelled/removed it while the task
            // was unwinding. Honor that instead of recording an error or scheduling a retry.
            if (repository.getEntity(entity.id)?.status != DownloadStatus.RUNNING) return@withLock
            val attempt = entity.retryCount
            if (error.isRetryable && attempt < entity.maxRetries) {
                logger.i("Retrying ${entity.id} (attempt ${attempt + 1}/${entity.maxRetries})")
                scheduleRetry(entity, error, attempt + 1)
            } else {
                repository.setError(entity.id, error, attempt)
            }
        }
    }

    /**
     * Schedules a delayed retry. The timer is tracked in [retryJobs] so a pause/cancel/remove issued
     * during the backoff window cancels it; otherwise the timer would re-queue a download the user
     * just stopped. Must hold [scheduleMutex].
     */
    private fun scheduleRetry(
        entity: DownloadEntity,
        error: DownloadError,
        nextAttempt: Int,
    ) {
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                repository.setError(entity.id, error, nextAttempt)
                delay(backoffMillis(entity, nextAttempt, error).milliseconds)
                scheduleMutex.withLock {
                    // Re-queue only if still FAILED (not paused/cancelled/removed meanwhile).
                    if (repository.getEntity(entity.id)?.status == DownloadStatus.FAILED) {
                        repository.setStatus(entity.id, DownloadStatus.QUEUED)
                    }
                }
                schedule()
            }
        retryJobs[entity.id] = job
        job.invokeOnCompletion { retryJobs.remove(entity.id, job) }
        job.start()
    }

    private fun backoffMillis(
        entity: DownloadEntity,
        attempt: Int,
        error: DownloadError,
    ): Long {
        if (error is DownloadError.Http && error.statusCode == 429) {
            error.retryAfterSeconds?.let { return it * 1000L }
        }
        var backoff = entity.initialBackoffMillis.toDouble()
        repeat(attempt) { backoff *= entity.backoffMultiplier }
        return backoff.toLong().coerceAtMost(entity.maxBackoffMillis)
    }

    private fun onJobFinished(
        id: String,
        job: Job,
    ) {
        // Only clear the slot if it still holds this job; a newer start() may have replaced it.
        activeJobs.remove(id, job)
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
