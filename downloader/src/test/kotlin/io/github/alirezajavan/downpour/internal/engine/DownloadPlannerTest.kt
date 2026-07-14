package io.github.alirezajavan.downpour.internal.engine

import com.google.common.truth.Truth.assertThat
import io.github.alirezajavan.downpour.api.DownloadManagerConfig
import io.github.alirezajavan.downpour.api.DownloadSchedule
import io.github.alirezajavan.downpour.internal.data.DownloadRepository
import io.github.alirezajavan.downpour.internal.data.db.DownloadEntity
import io.github.alirezajavan.downpour.internal.data.db.DownloadPartEntity
import io.github.alirezajavan.downpour.internal.network.RemoteFileInfo
import io.github.alirezajavan.downpour.internal.util.NoOpLogger
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DownloadPlannerTest {
    private val repository = mockk<DownloadRepository>(relaxed = true)
    private val config = DownloadManagerConfig(minSizeForMultiConnection = 1000)
    private val planner = DownloadPlanner(repository, config, NoOpLogger)

    @Test
    fun `plan returns single connection when file is small`() =
        runTest {
            val entity = createMockEntity(maxConnections = 4)
            val info = RemoteFileInfo(500, true, null, null, null, null)

            val plan = planner.plan(entity, info, 0)

            assertThat(plan.isMultiConnection).isFalse()
            assertThat(plan.parts).hasSize(1)
        }

    @Test
    fun `plan returns multi connection when conditions are met`() =
        runTest {
            val entity = createMockEntity(maxConnections = 4)
            val info = RemoteFileInfo(2000, true, "etag", null, null, null)
            coEvery { repository.getParts(any()) } returns
                listOf(
                    DownloadPartEntity(1, "id", 0, 0, 499, 0),
                    DownloadPartEntity(2, "id", 1, 500, 999, 500),
                    DownloadPartEntity(3, "id", 2, 1000, 1499, 1000),
                    DownloadPartEntity(4, "id", 3, 1500, 1999, 1500),
                )

            val plan = planner.plan(entity, info, 0)

            assertThat(plan.isMultiConnection).isTrue()
            assertThat(plan.parts).hasSize(4)
        }

    @Test
    fun `plan returns multi connection when connections gt 1 and size gt min`() =
        runTest {
            val entity = createMockEntity(maxConnections = 4)
            val info = RemoteFileInfo(2000, true, "etag", null, null, null)
            coEvery { repository.getParts(any()) } returns emptyList()
            coEvery { repository.replaceParts(any()) } returns Unit

            val plan = planner.plan(entity, info, 0)

            assertThat(plan.isMultiConnection).isTrue()
        }

    @Test
    fun `plan returns single connection when connections is 1`() =
        runTest {
            val entity = createMockEntity(maxConnections = 1)
            val info = RemoteFileInfo(2000, true, "etag", null, null, null)

            val plan = planner.plan(entity, info, 0)

            assertThat(plan.isMultiConnection).isFalse()
        }

    @Test
    fun `plan scales up when activeConnections is greater than current parts`() =
        runTest {
            val entity = createMockEntity(maxConnections = 2).copy(etag = "etag")
            val info = RemoteFileInfo(2000, true, "etag", null, null, null)

            // Mock current 2 parts
            val parts =
                listOf(
                    DownloadPartEntity(1, "id", 0, 0, 999, 0),
                    DownloadPartEntity(2, "id", 1, 1000, 1999, 1000),
                )
            coEvery { repository.getParts("id") } returns parts
            coEvery { repository.replaceParts(any()) } returns Unit

            // Plan with 4 connections
            val plan = planner.plan(entity, info, 0, activeConnections = 4)

            assertThat(plan.parts).hasSize(4)
        }

    @Test
    fun `plan scales down when activeConnections is less than current parts`() =
        runTest {
            val entity = createMockEntity(maxConnections = 4).copy(etag = "etag")
            val info = RemoteFileInfo(2000, true, "etag", null, null, null)

            // Mock current 4 parts
            val parts =
                listOf(
                    DownloadPartEntity(1, "id", 0, 0, 499, 0),
                    DownloadPartEntity(2, "id", 1, 500, 999, 500),
                    DownloadPartEntity(3, "id", 2, 1000, 1499, 1000),
                    DownloadPartEntity(4, "id", 3, 1500, 1999, 1500),
                )
            coEvery { repository.getParts("id") } returns parts
            coEvery { repository.replaceParts(any()) } returns Unit

            // Plan with 2 connections
            val plan = planner.plan(entity, info, 0, activeConnections = 2)

            assertThat(plan.parts).hasSize(2)
        }

    @Test
    fun `plan collapses to 1 connection using persisted part progress, not the preallocated file length`() =
        runTest {
            // Regression test: a multi-connection download preallocates the destination file to its
            // full size up front, so fileLength no longer reflects real progress once that happens.
            // A 429 concurrency downgrade to 1 connection must resume from the persisted per-part
            // offsets (via scaleDown), not misread the full preallocated fileLength as "done".
            val entity = createMockEntity(maxConnections = 5).copy(etag = "etag")
            val info = RemoteFileInfo(2000, true, "etag", null, null, null)

            val parts =
                listOf(
                    DownloadPartEntity(1, "id", 0, 0, 499, 0),
                    DownloadPartEntity(2, "id", 1, 500, 999, 700), // 200 bytes of real progress
                    DownloadPartEntity(3, "id", 2, 1000, 1499, 1000),
                    DownloadPartEntity(4, "id", 3, 1500, 1999, 1500),
                )
            coEvery { repository.getParts("id") } returns parts
            coEvery { repository.replaceParts(any()) } returns Unit

            // fileLength simulates the preallocated file: full size, even though almost nothing was
            // actually written.
            val plan = planner.plan(entity, info, fileLength = 2000, activeConnections = 1)

            assertThat(plan.isMultiConnection).isTrue()
            assertThat(plan.parts).hasSize(1)
            // Progress must come from the persisted parts, not fileLength (2000, i.e. "fully done").
            assertThat(plan.parts.single().downloaded).isLessThan(2000L)
        }

    private fun createMockEntity(maxConnections: Int) =
        DownloadEntity(
            id = "id",
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
            maxConnections = maxConnections,
            maxBytesPerSecond = 0,
            checksumAlgorithm = null,
            checksumValue = null,
            maxRetries = 0,
            initialBackoffMillis = 0,
            backoffMultiplier = 0.0,
            maxBackoffMillis = 0,
            schedule = DownloadSchedule(),
            status = io.github.alirezajavan.downpour.internal.data.DownloadStatus.QUEUED,
            downloadedBytes = 0,
            totalBytes = 0,
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
