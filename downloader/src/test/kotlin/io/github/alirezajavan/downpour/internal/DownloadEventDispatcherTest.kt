package io.github.alirezajavan.downpour.internal

import com.google.common.truth.Truth.assertThat
import io.github.alirezajavan.downpour.api.DownloadListener
import io.github.alirezajavan.downpour.api.DownloadRequest
import io.github.alirezajavan.downpour.internal.data.DownloadRepository
import io.github.alirezajavan.downpour.internal.data.DownloadStatus
import io.github.alirezajavan.downpour.internal.data.db.FakeDownloadDao
import io.github.alirezajavan.downpour.internal.data.toEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadEventDispatcherTest {
    private val dao = FakeDownloadDao()
    private val repository = DownloadRepository(dao) { 0L }

    @Test
    fun `fires once per lifecycle phase, not on progress ticks`() =
        runTest(UnconfinedTestDispatcher()) {
            val phases = mutableListOf<String>()
            DownloadEventDispatcher(
                backgroundScope,
                repository,
                listOf(DownloadListener { phases += it.state::class.simpleName.orEmpty() }),
            )

            repository.insert(request().toEntity("a", 0L))
            repository.setStatus("a", DownloadStatus.RUNNING)
            repository.setRunningProgress("a", downloaded = 10, total = 100, speed = 1, eta = 1)
            repository.setRunningProgress("a", downloaded = 50, total = 100, speed = 1, eta = 1)
            repository.setStatus("a", DownloadStatus.COMPLETED)
            advanceUntilIdle()

            assertThat(phases).containsExactly("Queued", "Running", "Completed").inOrder()
        }

    private fun request() = DownloadRequest.Builder("https://example.com/a", "/downloads/a.bin").build()
}
