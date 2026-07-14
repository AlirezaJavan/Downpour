package io.github.alirezajavan.downpour.internal.engine

import io.github.alirezajavan.downpour.api.DownloadDestination
import io.github.alirezajavan.downpour.api.DownloadError
import io.github.alirezajavan.downpour.api.DownloadManagerConfig
import io.github.alirezajavan.downpour.api.NetworkType
import io.github.alirezajavan.downpour.internal.data.DownloadRepository
import io.github.alirezajavan.downpour.internal.data.DownloadStatus
import io.github.alirezajavan.downpour.internal.data.db.DownloadEntity
import io.github.alirezajavan.downpour.internal.device.DeviceState
import io.github.alirezajavan.downpour.internal.device.DeviceStateMonitor
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
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

internal interface DownloadScheduler {
    fun schedule(delayMillis: Long)
}

internal class DownloadEngine(
    private val scope: CoroutineScope,
    private val repository: DownloadRepository,
    private val taskFactory: () -> DownloadTaskRunner,
    private val config: DownloadManagerConfig,
    private val serviceController: DownloadServiceController,
    private val networkMonitor: NetworkMonitor,
    private val deviceStateMonitor: DeviceStateMonitor,
    private val scheduler: DownloadScheduler,
    private val fileStore: FileStore,
    private val logger: Logger,
) {
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val retryJobs = ConcurrentHashMap<String, Job>()
    private val tuningJobs = ConcurrentHashMap<String, Job>()

    // Downloads the server has 429'd for concurrency. Adaptive tuning is never (re)started for
    // these: a fresh ConnectionTuner always tries to *increase* connections on its first evaluation
    // (see ConnectionTuner.decide), which has no memory of a 429 that just happened and would
    // immediately re-trigger it -- fighting the downgrade in a loop that burns the retry budget.
    // Once the server has said no to concurrency, we take it at its word for the rest of this
    // download rather than re-probing.
    private val rateLimitedDownloads = ConcurrentHashMap.newKeySet<String>()

    /**
     * Serializes every state transition. All status writes plus (de)registration of running jobs
     * and retry timers happen under this lock, so the user-facing operations (pause/resume/cancel/
     * retry/remove and their bulk/tag variants) can never interleave with the scheduler's
     * [start]/[schedule]. This is what makes "I paused, but it kept downloading" impossible: a pause
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
            config.expireCompletedAfter?.let { repository.deleteExpiredCompleted(it.inWholeMilliseconds) }
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
        scope.launch { deviceStateMonitor.changes.drop(1).collect { schedule() } }
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
                clearRateLimited(entity.id)
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
                clearRateLimited(entity.id)
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

    suspend fun setPriority(
        id: String,
        priority: Int,
    ) {
        scheduleMutex.withLock { repository.setPriority(id, priority) }
        schedule()
    }

    suspend fun moveToFront(id: String) {
        scheduleMutex.withLock { repository.moveToFront(id) }
        schedule()
    }

    suspend fun cancel(id: String) {
        scheduleMutex.withLock {
            val entity = repository.getEntity(id) ?: return@withLock
            cancelRetry(id)
            repository.setStatus(id, DownloadStatus.CANCELLED)
            cancelJob(id)
            clearRateLimited(id)
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
            // Snapshot the rows about to be canceled so their on-disk artifacts can be discarded.
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
            clearRateLimited(id)
            val entity = repository.getEntity(id)
            if (deleteFile) entity?.let { fileStore.delete(it.toDestination()) }
            repository.delete(id)
        }
        schedule()
    }

    private fun cancelActiveJobs() {
        activeJobs.keys.toList().forEach {
            cancelJob(it)
            clearRateLimited(it)
        }
    }

    /**
     * Cancels the running job for [id]. Must hold [scheduleMutex].
     *
     * Deliberately does NOT join the job. Joining here would deadlock — the task's completion path
     * ([onCompleted]/[onFailed]) re-acquires [scheduleMutex] — and would also stall every pause on
     * the in-flight socket read. Correctness instead comes from two guarantees that hold regardless
     * of when the canceled task finally unwinds:
     *  1. The caller has already written the new status (PAUSED/CANCELLED) under this lock, and the
     *     task only writes progress via [DownloadRepository.setRunningProgress], a no-op once the row
     *     is not RUNNING — so it can never advance a paused/canceled row.
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
            val device = deviceStateMonitor.snapshot()
            logger.d("Scheduling downloads. Network status: $status, Device status: $device")

            activeJobs.forEach { (id, _) ->
                val entity = repository.getEntity(id) ?: return@forEach
                val networkSatisfied = status.satisfies(NetworkType.entries[entity.networkType])
                val deviceSatisfied = device.satisfiesConstraints(entity)

                if (!networkSatisfied) {
                    repository.setStatus(id, DownloadStatus.WAITING_FOR_NETWORK)
                    cancelJob(id)
                } else if (!deviceSatisfied) {
                    val statusToSet =
                        if (device.isTimeRestricted(entity)) {
                            DownloadStatus.SCHEDULED
                        } else {
                            DownloadStatus.QUEUED
                        }
                    repository.setStatus(id, statusToSet)
                    cancelJob(id)
                }
            }

            repository.nextQueued(QUEUE_SCAN_LIMIT).forEach { entity ->
                if (activeJobs.containsKey(entity.id)) return@forEach

                val networkSatisfied = status.satisfies(NetworkType.entries[entity.networkType])
                val deviceSatisfied = device.satisfiesConstraints(entity)

                val targetStatus =
                    when {
                        !networkSatisfied -> DownloadStatus.WAITING_FOR_NETWORK
                        !deviceSatisfied -> if (device.isTimeRestricted(entity)) DownloadStatus.SCHEDULED else DownloadStatus.QUEUED
                        else -> DownloadStatus.QUEUED
                    }

                if (entity.status != targetStatus) {
                    repository.setStatus(entity.id, targetStatus)
                }
            }

            val freeSlots = config.maxConcurrentDownloads - activeJobs.size
            if (freeSlots > 0) startEligible(freeSlots)
            scheduleNextWakeup()
            serviceController.onActiveCountChanged(activeJobs.size)
        }

    private suspend fun scheduleNextWakeup() {
        val scheduled = repository.entitiesByStatuses(listOf(DownloadStatus.SCHEDULED))
        if (scheduled.isEmpty()) return

        val now = System.currentTimeMillis()
        val nextDelayMillis =
            scheduled.minOf { entity ->
                val targetTime = calculateTargetTime(entity, now)
                (targetTime - now).coerceAtLeast(0)
            }

        if (nextDelayMillis > 0 && nextDelayMillis != Long.MAX_VALUE) {
            logger.d("Scheduling wakeup for next event in ${nextDelayMillis / 1000}s")
            scheduler.schedule(nextDelayMillis)
        }
    }

    private fun calculateTargetTime(
        entity: DownloadEntity,
        now: Long,
    ): Long {
        val dateTarget = entity.scheduledAtMillis ?: now
        val calendar = Calendar.getInstance().apply { timeInMillis = dateTarget }
        val minuteAtDateTarget = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        val start = entity.scheduleStartMinuteOfDay ?: return dateTarget
        val end = entity.scheduleEndMinuteOfDay ?: return dateTarget

        // Is minuteAtDateTarget in window [start, end)?
        val inWindow =
            if (start < end) {
                minuteAtDateTarget in start until end
            } else {
                // Window crosses midnight
                minuteAtDateTarget >= start || minuteAtDateTarget < end
            }

        if (inWindow && dateTarget >= now) return dateTarget

        // Not in window or target was in the past (which should have started already).
        // Find the FIRST window start time that is >= dateTarget.
        val diffMinutes =
            if (start >= minuteAtDateTarget) {
                start - minuteAtDateTarget
            } else {
                (1440 - minuteAtDateTarget) + start
            }

        return dateTarget + diffMinutes * 60 * 1000L
    }

    private suspend fun startEligible(slots: Int) {
        val status = networkMonitor.snapshot()
        val device = deviceStateMonitor.snapshot()
        repository
            .nextQueued(QUEUE_SCAN_LIMIT)
            .asSequence()
            .filterNot { activeJobs.containsKey(it.id) }
            .filter { status.satisfies(NetworkType.entries[it.networkType]) }
            .filter { device.satisfiesConstraints(it) }
            // Global safety: don't start new downloads if the system already reports low storage.
            .filter { !device.isStorageLow }
            .take(slots)
            .forEach { start(it) }
    }

    private fun DeviceState.satisfiesConstraints(entity: DownloadEntity): Boolean =
        satisfies(
            entity.requiresCharging,
            entity.requiresBatteryNotLow,
            entity.requiresStorageNotLow,
            entity.scheduleStartMinuteOfDay,
            entity.scheduleEndMinuteOfDay,
            entity.scheduledAtMillis,
        )

    private fun DeviceState.isTimeRestricted(entity: DownloadEntity): Boolean {
        if (entity.scheduledAtMillis != null && currentTimeMillis < entity.scheduledAtMillis) {
            return true
        }

        val start = entity.scheduleStartMinuteOfDay ?: return false
        val end = entity.scheduleEndMinuteOfDay ?: return false

        return if (start < end) {
            currentTimeMinuteOfDay !in start until end
        } else {
            currentTimeMinuteOfDay < start && currentTimeMinuteOfDay >= end
        }
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
        job.invokeOnCompletion {
            onJobFinished(entity.id, job)
            cancelTuning(entity.id)
        }
        repository.setStatus(entity.id, DownloadStatus.RUNNING)

        if (config.adaptiveConcurrency && entity.supportsResume && entity.id !in rateLimitedDownloads) {
            startAdaptiveTuning(entity.id, entity.maxConnections)
        }

        job.start()
    }

    private fun startAdaptiveTuning(
        id: String,
        maxConnections: Int,
    ) {
        cancelTuning(id)
        val tuner = ConnectionTuner(config.minConnections, maxConnections.coerceAtMost(MAX_PARTS))
        val job =
            scope.launch {
                while (true) {
                    delay(config.concurrencyReevaluationInterval)
                    val entity = repository.getEntity(id) ?: break
                    if (entity.status != DownloadStatus.RUNNING) break

                    val current =
                        if (entity.effectiveConnections > 0) {
                            entity.effectiveConnections
                        } else {
                            entity.maxConnections
                        }
                    val next = tuner.decide(current, entity.bytesPerSecond)

                    if (next != current) {
                        logger.d("Adaptive concurrency tuning for $id: $current -> $next")
                        replan(id, next)
                        break
                    }
                }
            }
        tuningJobs[id] = job
    }

    private suspend fun replan(
        id: String,
        connections: Int,
    ) {
        scheduleMutex.withLock {
            val entity = repository.getEntity(id) ?: return@withLock
            if (entity.status != DownloadStatus.RUNNING) return@withLock
            repository.setEffectiveConnections(id, connections)
            // Requeue rather than mutate the running transfer directly: cancelJob alone leaves the
            // row RUNNING with no active job, and nextQueued() only scans QUEUED/WAITING_FOR_NETWORK,
            // so the download would never restart.
            repository.setStatus(id, DownloadStatus.QUEUED)
            cancelJob(id)
        }
    }

    private fun cancelTuning(id: String) {
        tuningJobs.remove(id)?.cancel()
    }

    private fun markRateLimited(id: String) {
        rateLimitedDownloads.add(id)
    }

    private fun clearRateLimited(id: String) {
        rateLimitedDownloads.remove(id)
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
        clearRateLimited(entity.id)
        logger.i("Download completed: ${entity.id}")
        runPostProcessing(entity)
    }

    private suspend fun runPostProcessing(entity: DownloadEntity) {
        if (entity.workerClass == null && config.postProcessors.isEmpty()) return
        val item = repository.getItem(entity.id) ?: return
        entity.workerClass?.let { config.workerFactory.create(it)?.process(item) }
        config.postProcessors.forEach { processor ->
            runCatching { processor.process(item) }
                .onFailure { logger.e("Post-processor failed for ${entity.id}", it) }
        }
    }

    private suspend fun onFailed(
        entity: DownloadEntity,
        error: DownloadError,
    ) {
        logger.e("Download failed: ${entity.id}. Error: ${error.message}", error)
        scheduleMutex.withLock {
            // If the row is no longer RUNNING, the user paused/canceled/removed it while the task
            // was unwinding. Honor that instead of recording an error or scheduling a retry.
            if (repository.getEntity(entity.id)?.status != DownloadStatus.RUNNING) return@withLock
            val attempt = entity.retryCount
            val effectiveError = downgradeConnectionsIfRateLimited(entity, error)
            if (effectiveError.isRetryable && attempt < entity.maxRetries) {
                logger.i("Retrying ${entity.id} (attempt ${attempt + 1}/${entity.maxRetries})")
                scheduleRetry(entity, effectiveError, attempt + 1)
            } else {
                logger.w(
                    "${entity.id} giving up permanently " +
                        "(retryable=${effectiveError.isRetryable}, attempt=$attempt/${entity.maxRetries})",
                )
                repository.setError(entity.id, effectiveError, attempt)
            }
        }
    }

    /**
     * A 429 while using more than one connection usually means the server is rate-limiting
     * concurrent range requests rather than the download itself, so retrying with the same
     * connection count would just loop into the same error. Drop this download to a single
     * connection (persisted via [DownloadRepository.setEffectiveConnections], which the planner
     * honors on the next attempt regardless of the adaptive-concurrency setting) and surface that
     * decision through the error message so it reaches the UI via the normal Failed state.
     */
    private suspend fun downgradeConnectionsIfRateLimited(
        entity: DownloadEntity,
        error: DownloadError,
    ): DownloadError {
        if (error !is DownloadError.Http || error.statusCode != HTTP_TOO_MANY_REQUESTS) return error
        val currentConnections =
            if (entity.effectiveConnections > 0) entity.effectiveConnections else entity.maxConnections
        markRateLimited(entity.id)
        if (currentConnections <= 1) {
            logger.w(
                "${entity.id} got HTTP 429 while already at 1 connection; server is still rate-limiting " +
                    "(Retry-After=${error.retryAfterSeconds}s). Will retry per backoff policy without further downgrade.",
            )
            return error
        }

        repository.setEffectiveConnections(entity.id, 1)
        logger.i(
            "Server rate-limited ${entity.id} with $currentConnections connections; " +
                "reducing to 1 connection and retrying",
        )
        return DownloadError.Http(
            statusCode = HTTP_TOO_MANY_REQUESTS,
            retryAfterSeconds = error.retryAfterSeconds,
            message =
                "Server is rate-limiting concurrent connections (HTTP 429). " +
                    "Reduced from $currentConnections to 1 connection and retrying.",
        )
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
                val delayMillis = backoffMillis(entity, nextAttempt, error)
                logger.d("Backoff for ${entity.id} before attempt $nextAttempt: ${delayMillis}ms")
                delay(delayMillis.milliseconds)
                scheduleMutex.withLock {
                    // Re-queue only if still FAILED (not paused/canceled/removed meanwhile).
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
        const val MAX_PARTS = 16
        const val HTTP_TOO_MANY_REQUESTS = 429
        val ACTIVE_STATUSES =
            listOf(
                DownloadStatus.QUEUED,
                DownloadStatus.RUNNING,
                DownloadStatus.WAITING_FOR_NETWORK,
                DownloadStatus.SCHEDULED,
            )
        val INTERRUPTIBLE_STATUSES =
            listOf(
                DownloadStatus.QUEUED,
                DownloadStatus.RUNNING,
                DownloadStatus.PAUSED,
                DownloadStatus.WAITING_FOR_NETWORK,
                DownloadStatus.SCHEDULED,
            )
    }
}
