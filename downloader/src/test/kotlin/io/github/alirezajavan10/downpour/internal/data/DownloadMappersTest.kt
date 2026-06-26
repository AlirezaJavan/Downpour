package io.github.alirezajavan10.downpour.internal.data

import com.google.common.truth.Truth.assertThat
import io.github.alirezajavan10.downpour.api.DownloadDestination
import io.github.alirezajavan10.downpour.api.Priority
import io.github.alirezajavan10.downpour.api.downloadRequest
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
