package io.github.alirezajavan10.downpour.internal.network

import com.google.common.truth.Truth.assertThat
import io.github.alirezajavan10.downpour.api.DownloadProgress
import io.github.alirezajavan10.downpour.internal.util.NoOpLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class HttpDownloadDataSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var dataSource: HttpDownloadDataSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dataSource = HttpDownloadDataSource(OkHttpClient(), Dispatchers.Unconfined, NoOpLogger)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `probe returns correct file info`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .addHeader("Content-Length", "1")
                    .addHeader("Content-Range", "bytes 0-0/1000")
                    .addHeader("ETag", "test-etag"),
            )

            val info = dataSource.probe(server.url("/").toString(), emptyMap())

            assertThat(info.totalBytes).isEqualTo(1000)
            assertThat(info.acceptsRanges).isTrue()
            assertThat(info.etag).isEqualTo("test-etag")
        }

    @Test
    fun `probe handles missing content length`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200))

            val info = dataSource.probe(server.url("/").toString(), emptyMap())

            assertThat(info.totalBytes).isEqualTo(DownloadProgress.UNKNOWN)
        }
}
