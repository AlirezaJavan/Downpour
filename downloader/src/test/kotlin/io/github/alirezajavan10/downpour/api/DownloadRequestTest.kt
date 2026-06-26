package io.github.alirezajavan10.downpour.api

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DownloadRequestTest {
    @Test
    fun `builder creates request with correct values`() {
        val request =
            DownloadRequest
                .Builder("https://example.com", "/path/to/file")
                .priority(Priority.HIGH)
                .tag("my-tag")
                .header("Key", "Value")
                .build()

        assertThat(request.url).isEqualTo("https://example.com")
        assertThat((request.destination as DownloadDestination.File).path).isEqualTo("/path/to/file")
        assertThat(request.priority).isEqualTo(Priority.HIGH)
        assertThat(request.tag).isEqualTo("my-tag")
        assertThat(request.headers["Key"]).isEqualTo("Value")
    }

    @Test
    fun `builder throws exception for blank url`() {
        assertThrows<IllegalArgumentException> {
            DownloadRequest.Builder("", "/path/to/file").build()
        }
    }

    @Test
    fun `downloadRequest DSL creates correct request`() {
        val request =
            downloadRequest("https://example.com", "/path/to/file") {
                priority(Priority.LOW)
                tag("dsl-tag")
            }

        assertThat(request.url).isEqualTo("https://example.com")
        assertThat(request.priority).isEqualTo(Priority.LOW)
        assertThat(request.tag).isEqualTo("dsl-tag")
    }
}
