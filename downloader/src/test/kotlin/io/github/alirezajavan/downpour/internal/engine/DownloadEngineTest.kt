package io.github.alirezajavan.downpour.internal.engine

import io.github.alirezajavan.downpour.api.DownloadManagerConfig
import io.github.alirezajavan.downpour.internal.data.DownloadRepository
import io.github.alirezajavan.downpour.internal.data.DownloadStatus
import io.github.alirezajavan.downpour.internal.data.db.DownloadEntity
import io.github.alirezajavan.downpour.internal.device.DeviceState
import io.github.alirezajavan.downpour.internal.device.DeviceStateMonitor
import io.github.alirezajavan.downpour.internal.network.NetworkMonitor
import io.github.alirezajavan.downpour.internal.network.NetworkStatus
import io.github.alirezajavan.downpour.internal.util.NoOpLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DownloadEngineTest {
    private val testScope = TestScope()
    private val repository = mockk<DownloadRepository>(relaxed = true)
    private val taskFactory = mockk<() -> DownloadTaskRunner>()
    private val config = DownloadManagerConfig()
    private val serviceController = mockk<DownloadServiceController>(relaxed = true)
    private val networkMonitor = mockk<NetworkMonitor>(relaxed = true)
    private val deviceStateMonitor = mockk<DeviceStateMonitor>(relaxed = true)
    private val fileStore = mockk<FileStore>(relaxed = true)

    private val engine =
        DownloadEngine(
            scope = testScope,
            repository = repository,
            taskFactory = taskFactory,
            config = config,
            serviceController = serviceController,
            networkMonitor = networkMonitor,
            deviceStateMonitor = deviceStateMonitor,
            fileStore = fileStore,
            logger = NoOpLogger,
        )

    @Test
    fun `recover resets running downloads to queued`() =
        runTest {
            coEvery { repository.setStatusIn(any(), any()) } returns Unit

            engine.recover()

            coVerify {
                repository.setStatusIn(
                    listOf(DownloadStatus.RUNNING, DownloadStatus.WAITING_FOR_NETWORK),
                    DownloadStatus.QUEUED,
                )
            }
        }

    @Test
    fun `pause updates status to paused`() =
        runTest {
            val id = "test-id"
            coEvery { repository.setStatus(id, DownloadStatus.PAUSED) } returns Unit

            engine.pause(id)

            coVerify { repository.setStatus(id, DownloadStatus.PAUSED) }
        }

    @Test
    fun `resume updates status to queued`() =
        runTest {
            val id = "test-id"
            coEvery { repository.setStatus(id, DownloadStatus.QUEUED) } returns Unit

            engine.resume(id)

            coVerify { repository.setStatus(id, DownloadStatus.QUEUED) }
        }

    @Test
    fun `cancel updates status and clears parts`() =
        runTest {
            val id = "test-id"
            val entity = mockk<DownloadEntity>(relaxed = true)
            every { entity.id } returns id
            coEvery { repository.getEntity(id) } returns entity

            engine.cancel(id)

            coVerify { repository.setStatus(id, DownloadStatus.CANCELLED) }
            coVerify { repository.clearParts(id) }
        }

    @Test
    fun `engine starts adaptive tuning when enabled and download supports resume`() =
        runTest {
            val adaptiveConfig = DownloadManagerConfig(adaptiveConcurrency = true)
            val engineWithAdaptive =
                DownloadEngine(
                    scope = testScope,
                    repository = repository,
                    taskFactory = taskFactory,
                    config = adaptiveConfig,
                    serviceController = serviceController,
                    networkMonitor = networkMonitor,
                    deviceStateMonitor = deviceStateMonitor,
                    fileStore = fileStore,
                    logger = NoOpLogger,
                )

            val entity = createMockEntity("id", supportsResume = true)
            coEvery { repository.nextQueued(any()) } returns listOf(entity)
            coEvery { repository.getEntity("id") } returns entity
            every { networkMonitor.snapshot() } returns NetworkStatus(isConnected = true, isMetered = false, isNotRoaming = true)
            every { deviceStateMonitor.snapshot() } returns DeviceState(isCharging = false, isBatteryLow = false, isStorageLow = false)
            val runner = mockk<DownloadTaskRunner>(relaxed = true)
            // Keep the task "running" for the duration of the test -- a relaxed mock that returns
            // immediately would complete the job inline and cancel adaptive tuning before it ever
            // gets a chance to poll.
            coEvery { runner.run(any()) } coAnswers { kotlinx.coroutines.awaitCancellation() }
            every { taskFactory.invoke() } returns runner

            engineWithAdaptive.onEnqueued()
            testScope.testScheduler.runCurrent()

            testScope.testScheduler.advanceTimeBy(adaptiveConfig.concurrencyReevaluationInterval.inWholeMilliseconds)
            testScope.testScheduler.runCurrent()

            coVerify { repository.getEntity("id") }
        }

    private fun createMockEntity(
        id: String,
        supportsResume: Boolean,
    ) = DownloadEntity(
        id = id,
        url = "url",
        destinationPath = "path",
        destinationType = 0,
        headers = emptyMap(),
        metadata = emptyMap(),
        tag = null,
        workerClass = null,
        priority = 0,
        conflictStrategy = 0,
        networkType = 0,
        maxConnections = 4,
        maxBytesPerSecond = 0,
        checksumAlgorithm = null,
        checksumValue = null,
        maxRetries = 0,
        initialBackoffMillis = 0,
        backoffMultiplier = 0.0,
        maxBackoffMillis = 0,
        status = DownloadStatus.QUEUED,
        downloadedBytes = 0,
        totalBytes = 1000,
        bytesPerSecond = 0,
        etaMillis = 0,
        supportsResume = supportsResume,
        etag = null,
        lastModified = null,
        retryCount = 0,
        errorType = null,
        errorMessage = null,
        errorHttpCode = null,
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )
}
