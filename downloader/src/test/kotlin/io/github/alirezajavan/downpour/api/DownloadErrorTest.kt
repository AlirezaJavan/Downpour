package io.github.alirezajavan.downpour.api

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DownloadErrorTest {
    @Test
    fun `transient and recoverable errors are retryable`() {
        assertThat(DownloadError.Connection(null).isRetryable).isTrue()
        assertThat(DownloadError.Timeout(null).isRetryable).isTrue()
        assertThat(DownloadError.Unknown(null).isRetryable).isTrue()
        assertThat(DownloadError.InsufficientStorage(100, 10).isRetryable).isTrue()
    }

    @Test
    fun `server errors and rate limiting are retryable but client errors are not`() {
        assertThat(DownloadError.Http(statusCode = 503).isRetryable).isTrue()
        assertThat(DownloadError.Http(statusCode = 429).isRetryable).isTrue()
        assertThat(DownloadError.Http(statusCode = 404).isRetryable).isFalse()
    }

    @Test
    fun `storage and validation failures are not retryable`() {
        assertThat(DownloadError.ContentValidation("bad").isRetryable).isFalse()
        assertThat(DownloadError.FileAlreadyExists("path").isRetryable).isFalse()
    }
}
