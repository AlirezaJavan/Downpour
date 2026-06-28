package io.github.alirezajavan.downpour.internal.data

import com.google.common.truth.Truth.assertThat
import io.github.alirezajavan.downpour.api.DownloadDestination
import io.github.alirezajavan.downpour.api.Priority
import io.github.alirezajavan.downpour.api.downloadRequest
import org.junit.jupiter.api.Test

class DownloadMappersTest {
    @Test
    fun `toEntity maps all fields correctly`() {
        val request =
            downloadRequest("https://example.com", "/path/to/file") {
                priority(Priority.HIGH)
                tag("test-tag")
            }
        val now = 123456789L
        val entity = request.toEntity("test-id", now)

        assertThat(entity.id).isEqualTo("test-id")
        assertThat(entity.url).isEqualTo("https://example.com")
        assertThat(entity.destinationPath).isEqualTo("/path/to/file")
        assertThat(entity.destinationType).isEqualTo(0)
        assertThat(entity.priority).isEqualTo(Priority.HIGH.ordinal)
        assertThat(entity.tag).isEqualTo("test-tag")
        assertThat(entity.createdAtMillis).isEqualTo(now)
    }

    @Test
    fun `toEntity carries mirrors and device constraints`() {
        val request =
            downloadRequest("https://example.com", "/path/to/file") {
                mirror("https://mirror.example/file")
                requiresCharging(true)
                requiresStorageNotLow(true)
            }
        val entity = request.toEntity("id", 50L)

        assertThat(entity.mirrors).containsExactly("https://mirror.example/file")
        assertThat(entity.requiresCharging).isTrue()
        assertThat(entity.requiresStorageNotLow).isTrue()
        assertThat(entity.sortKey).isEqualTo(50L)
    }

    @Test
    fun `toGroupProgress aggregates statuses and bytes`() {
        val a = downloadRequest("https://example.com/a", "/a").toEntity("a", 1L).copy(downloadedBytes = 30, totalBytes = 100)
        val b =
            downloadRequest("https://example.com/b", "/b")
                .toEntity("b", 2L)
                .copy(status = DownloadStatus.COMPLETED, downloadedBytes = 100, totalBytes = 100)

        val progress = listOf(a, b).toGroupProgress()

        assertThat(progress.total).isEqualTo(2)
        assertThat(progress.completed).isEqualTo(1)
        assertThat(progress.downloadedBytes).isEqualTo(130)
        assertThat(progress.totalBytes).isEqualTo(200)
    }

    @Test
    fun `toItem maps entity back to item correctly`() {
        val request =
            downloadRequest("https://example.com", "/path/to/file") {
                priority(Priority.HIGH)
            }
        val entity = request.toEntity("id", 100L)
        val item = entity.toItem()

        assertThat(item.id).isEqualTo("id")
        assertThat(item.url).isEqualTo("https://example.com")
        assertThat((item.destination as DownloadDestination.File).path).isEqualTo("/path/to/file")
        assertThat(item.priority).isEqualTo(Priority.HIGH)
    }
}
