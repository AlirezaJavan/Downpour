package io.github.alirezajavan.downpour.internal.engine

import io.github.alirezajavan.downpour.api.DownloadDestination
import io.github.alirezajavan.downpour.api.DownloadError
import io.github.alirezajavan.downpour.internal.network.HttpDownloadDataSource
import io.github.alirezajavan.downpour.internal.util.NoOpLogger
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicLong

class PartDownloaderTest {
    private val dataSource = mockk<HttpDownloadDataSource>()
    private val fileStore = mockk<FileStore>()
    private val partDownloader = PartDownloader(dataSource, fileStore, NoOpLogger)

    @Test
    fun `download calls datasource and writes to file`() =
        runTest {
            val part = PartPlan(0, 0, 0, 100, 0)
            val context =
                PartContext(
                    url = "url",
                    headers = emptyMap(),
                    part = part,
                    ifRange = null,
                    destination = DownloadDestination.File("path"),
                    isMultiConnection = false,
                    progress = AtomicLong(0),
                    partOffset = AtomicLong(0),
                    rateLimiters = emptyList(),
                )

            val response = mockk<Response>(relaxed = true)
            every { response.isSuccessful } returns true
            every { response.body } returns "data".toResponseBody()

            coEvery { dataSource.open(any(), any(), any(), any(), any()) } returns response

            val sink = mockk<RandomAccessSink>(relaxed = true)
            every { fileStore.openWritable(any()) } returns sink

            partDownloader.download(context)
        }

    @Test
    fun `download handles server range error`() =
        runTest {
            val part = PartPlan(0, 0, 10, 100, 0)
            val context =
                PartContext(
                    url = "url",
                    headers = emptyMap(),
                    part = part,
                    ifRange = null,
                    destination = DownloadDestination.File("path"),
                    isMultiConnection = true,
                    progress = AtomicLong(0),
                    partOffset = AtomicLong(0),
                    rateLimiters = emptyList(),
                )

            val response = mockk<Response>(relaxed = true)
            every { response.isSuccessful } returns true
            every { response.code } returns 200 // Should be 206

            coEvery { dataSource.open(any(), any(), any(), any(), any()) } returns response

            assertThrows<DownloadError.ContentValidation> {
                partDownloader.download(context)
            }
        }
}
