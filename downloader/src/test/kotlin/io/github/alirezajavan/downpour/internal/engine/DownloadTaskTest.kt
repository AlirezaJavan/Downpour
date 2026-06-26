package io.github.alirezajavan.downpour.internal.engine

import com.google.common.truth.Truth.assertThat
import io.github.alirezajavan.downpour.api.DownloadManagerConfig
import io.github.alirezajavan.downpour.internal.data.DownloadRepository
import io.github.alirezajavan.downpour.internal.data.DownloadStatus
import io.github.alirezajavan.downpour.internal.data.db.DownloadEntity
import io.github.alirezajavan.downpour.internal.network.HttpDownloadDataSource
import io.github.alirezajavan.downpour.internal.network.RemoteFileInfo
import io.github.alirezajavan.downpour.internal.util.NoOpLogger
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DownloadTaskTest {
    private val dataSource = mockk<HttpDownloadDataSource>()
    private val planner = mockk<DownloadPlanner>()
    private val partDownloader = mockk<PartDownloader>()
    private val repository = mockk<DownloadRepository>(relaxed = true)
    private val config = DownloadManagerConfig()
    private val globalRateLimiter = RateLimiter(0)
    private val fileStore = mockk<FileStore>(relaxed = true)

    private val task =
        DownloadTask(
            dataSource = dataSource,
            planner = planner,
            partDownloader = partDownloader,
            repository = repository,
            config = config,
            globalRateLimiter = globalRateLimiter,
            ioDispatcher = Dispatchers.Unconfined,
            fileStore = fileStore,
            logger = NoOpLogger,
        )

    @Test
    fun `run returns completed result on success`() =
        runTest {
            val entity = createMockEntity()
            val info =
                RemoteFileInfo(
                    totalBytes = 100,
                    acceptsRanges = true,
                    etag = "etag",
                    lastModified = "date",
                    contentType = "application/bin",
                    contentDisposition = null,
                )
            coEvery { dataSource.probe(any(), any()) } returns info
            coEvery { planner.plan(any(), any(), any(), any()) } returns DownloadPlan(100, emptyList(), null, false)
            every { fileStore.lengthOf(any()) } returns 100
            every { fileStore.usableSpaceFor(any()) } returns 1000

            val result = task.run(entity)

            assertThat(result).isInstanceOf(TaskResult.Completed::class.java)
        }

    private fun createMockEntity() =
        DownloadEntity(
            id = "id",
            url = "https://example.com",
            destinationPath = "path",
            destinationType = 0,
            headers = emptyMap(),
            metadata = emptyMap(),
            tag = null,
            workerClass = null,
            priority = 0,
            conflictStrategy = 0,
            networkType = 0,
            maxConnections = 1,
            maxBytesPerSecond = 0,
            checksumAlgorithm = null,
            checksumValue = null,
            maxRetries = 0,
            initialBackoffMillis = 0,
            backoffMultiplier = 0.0,
            maxBackoffMillis = 0,
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
            createdAtMillis = 0,
            updatedAtMillis = 0,
        )
}
