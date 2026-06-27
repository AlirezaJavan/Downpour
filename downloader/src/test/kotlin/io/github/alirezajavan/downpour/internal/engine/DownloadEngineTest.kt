package io.github.alirezajavan.downpour.internal.engine

import io.github.alirezajavan.downpour.api.DownloadManagerConfig
import io.github.alirezajavan.downpour.internal.data.DownloadRepository
import io.github.alirezajavan.downpour.internal.data.DownloadStatus
import io.github.alirezajavan.downpour.internal.data.db.DownloadEntity
import io.github.alirezajavan.downpour.internal.network.NetworkMonitor
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
    private val fileStore = mockk<FileStore>(relaxed = true)

    private val engine =
        DownloadEngine(
            scope = testScope,
            repository = repository,
            taskFactory = taskFactory,
            config = config,
            serviceController = serviceController,
            networkMonitor = networkMonitor,
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
}
