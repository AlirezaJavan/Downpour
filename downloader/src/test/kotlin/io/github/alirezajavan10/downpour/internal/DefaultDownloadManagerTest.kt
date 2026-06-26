package io.github.alirezajavan10.downpour.internal

import io.github.alirezajavan10.downpour.api.DownloadManagerConfig
import io.github.alirezajavan10.downpour.api.DownloadRequest
import io.github.alirezajavan10.downpour.internal.data.DownloadRepository
import io.github.alirezajavan10.downpour.internal.engine.DownloadEngine
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
    private val manager = DefaultDownloadManager(repository, engine, testScope, config)

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
}
