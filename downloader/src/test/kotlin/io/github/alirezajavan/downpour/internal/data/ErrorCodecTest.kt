package io.github.alirezajavan.downpour.internal.data

import com.google.common.truth.Truth.assertThat
import io.github.alirezajavan.downpour.api.DownloadError
import org.junit.jupiter.api.Test

class ErrorCodecTest {
    @Test
    fun `http error round trips with its status code`() {
        val encoded = ErrorCodec.encode(DownloadError.Http(statusCode = 503))

        val decoded = ErrorCodec.decode(encoded.type, encoded.message, encoded.httpCode)

        assertThat(decoded).isInstanceOf(DownloadError.Http::class.java)
        assertThat((decoded as DownloadError.Http).statusCode).isEqualTo(503)
    }

    @Test
    fun `connection error round trips to the same type`() {
        val encoded = ErrorCodec.encode(DownloadError.Connection(null))

        val decoded = ErrorCodec.decode(encoded.type, encoded.message, encoded.httpCode)

        assertThat(decoded).isInstanceOf(DownloadError.Connection::class.java)
    }

    @Test
    fun `file already exists error round trips`() {
        val encoded = ErrorCodec.encode(DownloadError.FileAlreadyExists("path"))
        val decoded = ErrorCodec.decode(encoded.type, encoded.message, encoded.httpCode)
        assertThat(decoded).isInstanceOf(DownloadError.FileAlreadyExists::class.java)
    }

    @Test
    fun `unknown type decodes to the unknown error`() {
        val decoded = ErrorCodec.decode(type = null, message = null, httpCode = null)

        assertThat(decoded).isInstanceOf(DownloadError.Unknown::class.java)
    }
}
