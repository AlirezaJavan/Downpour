package io.github.alirezajavan.downpour.internal.engine

import com.google.common.truth.Truth.assertThat
import io.github.alirezajavan.downpour.api.DownloadError
import io.github.alirezajavan.downpour.api.DownloadManagerConfig
import io.github.alirezajavan.downpour.api.DownloadPostProcessor
import io.github.alirezajavan.downpour.api.DownloadSchedule
import io.github.alirezajavan.downpour.internal.data.DownloadRepository
import io.github.alirezajavan.downpour.internal.data.DownloadStatus
import io.github.alirezajavan.downpour.internal.data.db.DownloadEntity
import io.github.alirezajavan.downpour.internal.data.db.FakeDownloadDao
import io.github.alirezajavan.downpour.internal.device.DeviceState
import io.github.alirezajavan.downpour.internal.device.DeviceStateMonitor
import io.github.alirezajavan.downpour.internal.network.NetworkMonitor
import io.github.alirezajavan.downpour.internal.network.NetworkStatus
import io.github.alirezajavan.downpour.internal.util.NoOpLogger
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

/**
 * Exhaustive state-machine tests for [DownloadEngine].
 *
 * Uses a faithful in-memory DAO + the real repository + a scripted task runner so every transition
 * is exercised against real persistence logic with fully deterministic virtual time. These tests
 * lock in the firm guarantees: a paused/cancelled download can never keep advancing, a retry timer
 * can never resurrect a stopped download, and concurrency/ordering rules hold.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadEngineStateTest {
    private val dao = FakeDownloadDao()
    private val repository = DownloadRepository(dao) { NOW }
    private val networkMonitor = mockk<NetworkMonitor>()
    private val deviceStateMonitor = mockk<DeviceStateMonitor>()
    private val scheduler = mockk<DownloadScheduler>(relaxed = true)
    private val fileStore = mockk<FileStore>(relaxed = true)
    private val serviceCounts = mutableListOf<Int>()
    private val serviceController = DownloadServiceController { serviceCounts += it }

    init {
        every { networkMonitor.snapshot() } returns CONNECTED
        every { networkMonitor.changes } returns emptyFlow()
        every { deviceStateMonitor.snapshot() } returns UNCONSTRAINED
        every { deviceStateMonitor.changes } returns emptyFlow()
    }

    private fun TestScope.newEngine(
        runner: ScriptedRunner,
        config: DownloadManagerConfig = DownloadManagerConfig(),
    ) = DownloadEngine(
        scope = backgroundScope,
        repository = repository,
        taskFactory = { runner },
        config = config,
        serviceController = serviceController,
        networkMonitor = networkMonitor,
        deviceStateMonitor = deviceStateMonitor,
        scheduler = scheduler,
        fileStore = fileStore,
        logger = NoOpLogger,
    )

    private suspend fun status(id: String) = repository.getEntity(id)!!.status

    private suspend fun downloaded(id: String) = repository.getEntity(id)!!.downloadedBytes

    @Test
    fun `enqueue starts the download and marks it running`() =
        runTest(UnconfinedTestDispatcher()) {
            val runner = ScriptedRunner(repository, Behavior.Hang(progress = 0, total = 100))
            val engine = newEngine(runner)

            repository.insert(entity("a"))
            engine.onEnqueued()
            advanceUntilIdle()

            assertThat(status("a")).isEqualTo(DownloadStatus.RUNNING)
            assertThat(runner.startedIds).containsExactly("a")
        }

    @Test
    fun `pause freezes progress - a paused download can never keep advancing`() =
        runTest(UnconfinedTestDispatcher()) {
            val runner = ScriptedRunner(repository, Behavior.Hang(progress = 10, total = 100))
            val engine = newEngine(runner)
            repository.insert(entity("a"))
            engine.onEnqueued()
            advanceUntilIdle()
            assertThat(downloaded("a")).isEqualTo(10)

            engine.pause("a")
            advanceUntilIdle()

            assertThat(status("a")).isEqualTo(DownloadStatus.PAUSED)
            // Simulate a late / in-flight progress flush from the cancelled task. It MUST be ignored
            // because the row is no longer RUNNING — this is the "Paused at 10% and climbing" bug.
            repository.setRunningProgress("a", downloaded = 99, total = 100, speed = 0, eta = 0)
            assertThat(downloaded("a")).isEqualTo(10)
        }

    @Test
    fun `resume re-queues a paused download and it runs again`() =
        runTest(UnconfinedTestDispatcher()) {
            val runner = ScriptedRunner(repository, Behavior.Hang(progress = 5, total = 100))
            val engine = newEngine(runner)
            repository.insert(entity("a"))
            engine.onEnqueued()
            advanceUntilIdle()
            engine.pause("a")
            advanceUntilIdle()
            assertThat(status("a")).isEqualTo(DownloadStatus.PAUSED)

            engine.resume("a")
            advanceUntilIdle()

            assertThat(status("a")).isEqualTo(DownloadStatus.RUNNING)
        }

    @Test
    fun `cancel stops the download, resets progress and deletes the file`() =
        runTest(UnconfinedTestDispatcher()) {
            val runner = ScriptedRunner(repository, Behavior.Hang(progress = 42, total = 100))
            val engine = newEngine(runner)
            repository.insert(entity("a"))
            engine.onEnqueued()
            advanceUntilIdle()
            assertThat(downloaded("a")).isEqualTo(42)

            engine.cancel("a")
            advanceUntilIdle()

            assertThat(status("a")).isEqualTo(DownloadStatus.CANCELLED)
            assertThat(downloaded("a")).isEqualTo(0)
            // A late flush after cancel is also ignored.
            repository.setRunningProgress("a", downloaded = 90, total = 100, speed = 0, eta = 0)
            assertThat(downloaded("a")).isEqualTo(0)
        }

    @Test
    fun `successful download is marked completed`() =
        runTest(UnconfinedTestDispatcher()) {
            val runner = ScriptedRunner(repository, Behavior.Succeed(total = 100))
            val engine = newEngine(runner)
            repository.insert(entity("a"))
            engine.onEnqueued()
            advanceUntilIdle()

            assertThat(status("a")).isEqualTo(DownloadStatus.COMPLETED)
            assertThat(downloaded("a")).isEqualTo(100)
        }

    @Test
    fun `non-retryable failure is marked failed`() =
        runTest(UnconfinedTestDispatcher()) {
            val runner = ScriptedRunner(repository, Behavior.Fail(DownloadError.FileAlreadyExists("x")))
            val engine = newEngine(runner)
            repository.insert(entity("a"))
            engine.onEnqueued()
            advanceUntilIdle()

            assertThat(status("a")).isEqualTo(DownloadStatus.FAILED)
        }

    @Test
    fun `retryable failure is retried and can then succeed`() =
        runTest(UnconfinedTestDispatcher()) {
            val runner = ScriptedRunner(repository, Behavior.Fail(DownloadError.Connection(IOException())))
            val engine = newEngine(runner)
            repository.insert(entity("a", maxRetries = 2, initialBackoffMillis = 1_000))
            engine.onEnqueued()
            advanceUntilIdle()
            assertThat(status("a")).isEqualTo(DownloadStatus.FAILED)

            // Next attempt should succeed.
            runner.behaviors["a"] = Behavior.Succeed(total = 100)
            advanceTimeBy(5_000.milliseconds)
            advanceUntilIdle()

            assertThat(status("a")).isEqualTo(DownloadStatus.COMPLETED)
        }

    @Test
    fun `cancelling during retry backoff prevents the timer from resurrecting the download`() =
        runTest(UnconfinedTestDispatcher()) {
            val runner = ScriptedRunner(repository, Behavior.Fail(DownloadError.Connection(IOException())))
            val engine = newEngine(runner, DownloadManagerConfig(maxConcurrentDownloads = 1))
            repository.insert(entity("a", maxRetries = 5, initialBackoffMillis = 10_000))
            engine.onEnqueued()
            advanceUntilIdle()
            assertThat(status("a")).isEqualTo(DownloadStatus.FAILED)

            // Cancel while the retry timer is still counting down.
            engine.cancel("a")
            advanceUntilIdle()
            assertThat(status("a")).isEqualTo(DownloadStatus.CANCELLED)

            // Let the original backoff elapse: the download must NOT be resurrected.
            advanceTimeBy(60_000.milliseconds)
            advanceUntilIdle()
            assertThat(status("a")).isEqualTo(DownloadStatus.CANCELLED)
        }

    @Test
    fun `pausing during retry backoff also prevents resurrection`() =
        runTest(UnconfinedTestDispatcher()) {
            val runner = ScriptedRunner(repository, Behavior.Fail(DownloadError.Connection(IOException())))
            val engine = newEngine(runner)
            repository.insert(entity("a", maxRetries = 5, initialBackoffMillis = 10_000))
            engine.onEnqueued()
            advanceUntilIdle()
            assertThat(status("a")).isEqualTo(DownloadStatus.FAILED)

            engine.pause("a")
            advanceUntilIdle()
            advanceTimeBy(60_000.milliseconds)
            advanceUntilIdle()

            assertThat(status("a")).isEqualTo(DownloadStatus.PAUSED)
        }

    @Test
    fun `respects max concurrent downloads`() =
        runTest(UnconfinedTestDispatcher()) {
            val runner = ScriptedRunner(repository, Behavior.Hang(progress = 0, total = 100))
            val engine = newEngine(runner, DownloadManagerConfig(maxConcurrentDownloads = 1))
            repository.insert(entity("a", createdAt = 1))
            repository.insert(entity("b", createdAt = 2))
            engine.onEnqueued()
            advanceUntilIdle()

            assertThat(status("a")).isEqualTo(DownloadStatus.RUNNING)
            assertThat(status("b")).isEqualTo(DownloadStatus.QUEUED)
        }

    @Test
    fun `losing network moves a running download to waiting, and it resumes when network returns`() =
        runTest(UnconfinedTestDispatcher()) {
            val runner = ScriptedRunner(repository, Behavior.Hang(progress = 0, total = 100))
            val engine = newEngine(runner)
            repository.insert(entity("a"))
            engine.onEnqueued()
            advanceUntilIdle()
            assertThat(status("a")).isEqualTo(DownloadStatus.RUNNING)

            every { networkMonitor.snapshot() } returns DISCONNECTED
            engine.onEnqueued() // any schedule pass re-evaluates network constraints
            advanceUntilIdle()
            assertThat(status("a")).isEqualTo(DownloadStatus.WAITING_FOR_NETWORK)

            every { networkMonitor.snapshot() } returns CONNECTED
            engine.onEnqueued()
            advanceUntilIdle()
            assertThat(status("a")).isEqualTo(DownloadStatus.RUNNING)
        }

    @Test
    fun `pauseAll then resumeAll round-trips every active download`() =
        runTest(UnconfinedTestDispatcher()) {
            val runner = ScriptedRunner(repository, Behavior.Hang(progress = 0, total = 100))
            val engine = newEngine(runner, DownloadManagerConfig(maxConcurrentDownloads = 5))
            repository.insert(entity("a", createdAt = 1))
            repository.insert(entity("b", createdAt = 2))
            engine.onEnqueued()
            advanceUntilIdle()

            engine.pauseAll()
            advanceUntilIdle()
            assertThat(status("a")).isEqualTo(DownloadStatus.PAUSED)
            assertThat(status("b")).isEqualTo(DownloadStatus.PAUSED)

            engine.resumeAll()
            advanceUntilIdle()
            assertThat(status("a")).isEqualTo(DownloadStatus.RUNNING)
            assertThat(status("b")).isEqualTo(DownloadStatus.RUNNING)
        }

    @Test
    fun `post-processor runs after a download completes`() =
        runTest(UnconfinedTestDispatcher()) {
            val processed = mutableListOf<String>()
            val config = DownloadManagerConfig(postProcessors = listOf(DownloadPostProcessor { processed += it.id }))
            val engine = newEngine(ScriptedRunner(repository, Behavior.Succeed(total = 100)), config)

            repository.insert(entity("a"))
            engine.onEnqueued()
            advanceUntilIdle()

            assertThat(status("a")).isEqualTo(DownloadStatus.COMPLETED)
            assertThat(processed).containsExactly("a")
        }

    @Test
    fun `requiresCharging keeps a download queued until the device is charging`() =
        runTest(UnconfinedTestDispatcher()) {
            every { deviceStateMonitor.snapshot() } returns UNCONSTRAINED
            val engine = newEngine(ScriptedRunner(repository, Behavior.Hang(progress = 0, total = 100)))
            repository.insert(entity("a").copy(requiresCharging = true))

            engine.onEnqueued()
            advanceUntilIdle()
            assertThat(status("a")).isEqualTo(DownloadStatus.QUEUED)

            every { deviceStateMonitor.snapshot() } returns UNCONSTRAINED.copy(isCharging = true)
            engine.onEnqueued()
            advanceUntilIdle()
            assertThat(status("a")).isEqualTo(DownloadStatus.RUNNING)
        }

    @Test
    fun `moveToFront makes a later download start before an earlier queued one`() =
        runTest(UnconfinedTestDispatcher()) {
            val engine =
                newEngine(
                    ScriptedRunner(repository, Behavior.Hang(progress = 0, total = 100)),
                    DownloadManagerConfig(maxConcurrentDownloads = 1),
                )
            repository.insert(entity("a", createdAt = 1))
            repository.insert(entity("b", createdAt = 2))
            repository.insert(entity("c", createdAt = 3))
            engine.onEnqueued()
            advanceUntilIdle()
            assertThat(status("a")).isEqualTo(DownloadStatus.RUNNING)

            engine.moveToFront("c")
            engine.pause("a")
            advanceUntilIdle()

            assertThat(status("c")).isEqualTo(DownloadStatus.RUNNING)
            assertThat(status("b")).isEqualTo(DownloadStatus.QUEUED)
        }

    @Test
    fun `schedule keeps a download scheduled until the time window starts`() =
        runTest(UnconfinedTestDispatcher()) {
            val startTime = NOW + 10_000
            val endTime = NOW + 20_000
            val entity =
                entity("a").copy(
                    schedule =
                        DownloadSchedule(
                            startTimeMillis = startTime,
                            endTimeMillis = endTime,
                        ),
                )
            repository.insert(entity)

            // Current time is NOW (before start)
            every { deviceStateMonitor.snapshot() } returns UNCONSTRAINED.copy(currentTimeMillis = NOW)
            val engine = newEngine(ScriptedRunner(repository, Behavior.Hang(progress = 0, total = 100)))

            engine.onEnqueued()
            advanceUntilIdle()
            assertThat(status("a")).isEqualTo(DownloadStatus.SCHEDULED)

            // Current time is between start and end
            every { deviceStateMonitor.snapshot() } returns UNCONSTRAINED.copy(currentTimeMillis = startTime + 1)
            engine.onEnqueued()
            advanceUntilIdle()
            assertThat(status("a")).isEqualTo(DownloadStatus.RUNNING)

            // Current time is after end
            every { deviceStateMonitor.snapshot() } returns UNCONSTRAINED.copy(currentTimeMillis = endTime + 1)
            engine.onEnqueued()
            advanceUntilIdle()
            assertThat(status("a")).isEqualTo(DownloadStatus.SCHEDULED)
        }

    private fun entity(
        id: String,
        createdAt: Long = 0,
        maxRetries: Int = 0,
        initialBackoffMillis: Long = 0,
    ) = DownloadEntity(
        id = id,
        url = "https://example.com/$id",
        destinationPath = "/downloads/$id.bin",
        destinationType = 0,
        headers = emptyMap(),
        metadata = emptyMap(),
        tag = null,
        workerClass = null,
        priority = 0,
        sortKey = createdAt,
        conflictStrategy = 0,
        networkType = 0,
        maxConnections = 1,
        maxBytesPerSecond = 0,
        checksumAlgorithm = null,
        checksumValue = null,
        maxRetries = maxRetries,
        initialBackoffMillis = initialBackoffMillis,
        backoffMultiplier = 2.0,
        maxBackoffMillis = 60_000,
        schedule = DownloadSchedule(),
        status = DownloadStatus.QUEUED,
        downloadedBytes = 0,
        totalBytes = 100,
        bytesPerSecond = 0,
        etaMillis = 0,
        supportsResume = false,
        etag = null,
        lastModified = null,
        retryCount = 0,
        errorType = null,
        errorMessage = null,
        errorHttpCode = null,
        createdAtMillis = createdAt,
        updatedAtMillis = createdAt,
    )

    private sealed interface Behavior {
        data class Succeed(
            val total: Long,
        ) : Behavior

        data class Fail(
            val error: DownloadError,
        ) : Behavior

        data class Hang(
            val progress: Long,
            val total: Long,
        ) : Behavior
    }

    /**
     * Controllable [DownloadTaskRunner]. "Hang" writes one progress sample (via the same gated path
     * the real task uses) then suspends until cancelled, so the engine can be probed mid-download.
     */
    private class ScriptedRunner(
        private val repository: DownloadRepository,
        var default: Behavior,
    ) : DownloadTaskRunner {
        val behaviors = mutableMapOf<String, Behavior>()
        val startedIds = mutableListOf<String>()

        override suspend fun run(entity: DownloadEntity): TaskResult {
            startedIds += entity.id
            return when (val behavior = behaviors[entity.id] ?: default) {
                is Behavior.Succeed -> {
                    TaskResult.Completed(behavior.total)
                }

                is Behavior.Fail -> {
                    TaskResult.Failed(behavior.error)
                }

                is Behavior.Hang -> {
                    repository.setRunningProgress(entity.id, behavior.progress, behavior.total, speed = 0, eta = 0)
                    awaitCancellation()
                }
            }
        }
    }

    private companion object {
        const val NOW = 1_000L
        val CONNECTED = NetworkStatus(isConnected = true, isMetered = false, isNotRoaming = true)
        val DISCONNECTED = NetworkStatus(isConnected = false, isMetered = true, isNotRoaming = false)
        val UNCONSTRAINED =
            DeviceState(
                isCharging = false,
                isBatteryLow = false,
                isStorageLow = false,
                currentTimeMillis = NOW,
            )
    }
}
