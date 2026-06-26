package io.github.alirezajavan10.downpour.internal.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.alirezajavan10.downpour.internal.data.db.DownloadDatabase
import io.github.alirezajavan10.downpour.internal.data.db.DownloadEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadRepositoryTest {
    private lateinit var database: DownloadDatabase
    private lateinit var repository: DownloadRepository

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    DownloadDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        repository = DownloadRepository(database.downloadDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert and get download works`() =
        runTest {
            val entity = createMockEntity("test-id")
            repository.insert(entity)

            val result = repository.getEntity("test-id")
            assertThat(result).isEqualTo(entity)
        }

    @Test
    fun `nextQueued returns only queued and waiting for network status`() =
        runTest {
            val queued = createMockEntity("id1").copy(status = DownloadStatus.QUEUED)
            val waiting = createMockEntity("id2").copy(status = DownloadStatus.WAITING_FOR_NETWORK)
            val completed = createMockEntity("id3").copy(status = DownloadStatus.COMPLETED)

            repository.insert(queued)
            repository.insert(waiting)
            repository.insert(completed)

            val result = repository.nextQueued(10)
            assertThat(result).hasSize(2)
            assertThat(result.map { it.id }).containsExactly("id1", "id2")
        }

    private fun createMockEntity(id: String) =
        DownloadEntity(
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
