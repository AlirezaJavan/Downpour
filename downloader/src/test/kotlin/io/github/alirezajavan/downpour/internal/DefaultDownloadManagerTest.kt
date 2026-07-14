package io.github.alirezajavan.downpour.internal

import com.google.common.truth.Truth.assertThat
import io.github.alirezajavan.downpour.api.ChecksumAlgorithm
import io.github.alirezajavan.downpour.api.ConflictStrategy
import io.github.alirezajavan.downpour.api.DownloadManagerConfig
import io.github.alirezajavan.downpour.api.DownloadRequest
import io.github.alirezajavan.downpour.api.DuplicatePolicy
import io.github.alirezajavan.downpour.api.NetworkType
import io.github.alirezajavan.downpour.api.Priority
import io.github.alirezajavan.downpour.internal.data.DownloadRepository
import io.github.alirezajavan.downpour.internal.engine.DownloadEngine
import io.github.alirezajavan.downpour.internal.util.NoOpLogger
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DefaultDownloadManagerTest {
    private val repository = mockk<DownloadRepository>(relaxed = true)
    private val engine = mockk<DownloadEngine>(relaxed = true)
    private val testScope = TestScope()
    private val config = DownloadManagerConfig()
    private val eventDispatcher = mockk<DownloadEventDispatcher>(relaxed = true)
    private val manager = DefaultDownloadManager(repository, engine, testScope, config, NoOpLogger, eventDispatcher)

    @Test
    fun `enqueue inserts into repository and notifies engine`() =
        runTest {
            val request = DownloadRequest.Builder("https://example.com", "path").build()
            manager.enqueue(request)

            testScope.testScheduler.runCurrent()

            coVerify { repository.insert(any()) }
            coVerify { engine.onEnqueued() }
        }

    @Test
    fun `pause calls engine pause`() =
        runTest {
            manager.pause("id")
            coVerify { engine.pause("id") }
        }

    @Test
    fun `enqueueAll returns an id per request`() =
        runTest {
            val requests =
                listOf(
                    DownloadRequest.Builder("https://example.com/a", "a").build(),
                    DownloadRequest.Builder("https://example.com/b", "b").build(),
                )

            val ids = manager.enqueueAll(requests)

            testScope.testScheduler.runCurrent()
            assertThat(ids).hasSize(2)
            coVerify(exactly = 2) { repository.insert(any()) }
        }

    @Test
    fun `moveToFront delegates to engine`() =
        runTest {
            manager.moveToFront("id")
            coVerify { engine.moveToFront("id") }
        }

    @Test
    fun `setPriority delegates to engine with ordinal`() =
        runTest {
            manager.setPriority("id", Priority.HIGH)
            coVerify { engine.setPriority("id", Priority.HIGH.ordinal) }
        }

    @Test
    fun `enqueue reuses existing id under REUSE_EXISTING`() =
        runTest {
            val request =
                DownloadRequest
                    .Builder("https://example.com", "path")
                    .duplicatePolicy(DuplicatePolicy.REUSE_EXISTING)
                    .build()
            io.mockk.coEvery { repository.findNonTerminalByUrlAndPath("https://example.com", "path") } returns "existing_id"

            val id = manager.enqueue(request)

            assertThat(id).isEqualTo("existing_id")
            coVerify(exactly = 0) { repository.insert(any()) }
        }

    @Test
    fun `enqueue creates new id under ALLOW_DUPLICATE`() =
        runTest {
            val request =
                DownloadRequest
                    .Builder("https://example.com", "path")
                    .duplicatePolicy(DuplicatePolicy.ALLOW_DUPLICATE)
                    .build()
            io.mockk.coEvery { repository.findNonTerminalByUrlAndPath(any(), any()) } returns null

            val id = manager.enqueue(request)

            testScope.testScheduler.runCurrent()
            assertThat(id).isNotEqualTo("existing_id")
            coVerify { repository.insert(any()) }
        }

    @Test
    fun `exportQueue and importQueue round-trip correctly`() =
        runTest {
            val entity =
                io.github.alirezajavan.downpour.internal.data.db.DownloadEntity(
                    id = "id1",
                    url = "https://example.com/file",
                    destinationPath = "path/file",
                    destinationType = 0,
                    headers = mapOf("Authorization" to "Bearer token"),
                    metadata = mapOf("key" to "value"),
                    tag = "tag1",
                    workerClass = "MyWorker",
                    priority = Priority.HIGH.ordinal,
                    conflictStrategy = ConflictStrategy.RENAME.ordinal,
                    networkType = NetworkType.UNMETERED.ordinal,
                    requiresCharging = true,
                    requiresBatteryNotLow = true,
                    requiresStorageNotLow = true,
                    schedule =
                        io.github.alirezajavan.downpour.api
                            .DownloadSchedule(1000L, 2000L),
                    maxConnections = 5,
                    maxBytesPerSecond = 1000L,
                    checksumAlgorithm = ChecksumAlgorithm.SHA256.ordinal,
                    checksumValue = "hash",
                    maxRetries = 3,
                    initialBackoffMillis = 1000L,
                    backoffMultiplier = 2.0,
                    maxBackoffMillis = 5000L,
                    status = io.github.alirezajavan.downpour.internal.data.DownloadStatus.QUEUED,
                    downloadedBytes = 0L,
                    totalBytes = 2000L,
                    bytesPerSecond = 0L,
                    etaMillis = 0L,
                    supportsResume = true,
                    etag = "etag",
                    lastModified = "lastModified",
                    retryCount = 0,
                    errorType = null,
                    errorMessage = null,
                    errorHttpCode = null,
                    createdAtMillis = 12345L,
                    updatedAtMillis = 12345L,
                )
            io.mockk.coEvery { repository.entitiesByStatuses(any()) } returns listOf(entity)

            val json = manager.exportQueue()

            assertThat(json).contains("https://example.com/file")
            assertThat(json).contains("token")
            assertThat(json).contains("MyWorker")

            io.mockk.coEvery { repository.findNonTerminalByUrlAndPath(any(), any()) } returns null
            val ids = manager.importQueue(json, ConflictStrategy.OVERWRITE)

            assertThat(ids).hasSize(1)
            testScope.testScheduler.runCurrent()
            coVerify { repository.insert(any()) }
        }
}
