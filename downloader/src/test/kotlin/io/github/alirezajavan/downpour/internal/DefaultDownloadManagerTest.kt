package io.github.alirezajavan.downpour.internal

import com.google.common.truth.Truth.assertThat
import io.github.alirezajavan.downpour.api.DownloadManagerConfig
import io.github.alirezajavan.downpour.api.DownloadRequest
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
}
