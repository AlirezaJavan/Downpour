package io.github.alirezajavan10.downpour.internal.network

import io.github.alirezajavan10.downpour.api.DownloadError
import io.github.alirezajavan10.downpour.api.DownloadProgress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException

internal class HttpDownloadDataSource(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun probe(
        url: String,
        headers: Map<String, String>,
    ): RemoteFileInfo =
        withContext(ioDispatcher) {
            val headRequest = buildRequest(url, headers) { head() }
            val headResponse =
                try {
                    execute(headRequest)
                } catch (_: IOException) {
                    null
                }

            headResponse?.use { response ->
                if (response.isSuccessful) {
                    val info = parseFileInfo(response)
                    if (info.totalBytes != DownloadProgress.UNKNOWN) return@withContext info
                }
            }

            val getRequest =
                buildRequest(url, headers) {
                    header(HEADER_RANGE, "$BYTES_UNIT=0-0")
                }
            execute(getRequest).use { response ->
                if (!response.isSuccessful) throw DownloadError.Http(response.code)
                parseFileInfo(response)
            }
        }

    fun open(
        url: String,
        headers: Map<String, String>,
        rangeStart: Long?,
        rangeEnd: Long?,
        ifRange: String?,
    ): Response {
        val request =
            buildRequest(url, headers) {
                if (rangeStart != null) {
                    val suffix = rangeEnd?.toString().orEmpty()
                    header(HEADER_RANGE, "$BYTES_UNIT=$rangeStart-$suffix")
                    ifRange?.let { header(HEADER_IF_RANGE, it) }
                }
            }
        return execute(request)
    }

    private fun execute(request: Request): Response =
        try {
            client.newCall(request).execute()
        } catch (timeout: SocketTimeoutException) {
            throw DownloadError.Timeout(timeout)
        } catch (io: IOException) {
            throw DownloadError.Connection(io)
        }

    private fun buildRequest(
        url: String,
        headers: Map<String, String>,
        block: Request.Builder.() -> Unit,
    ): Request =
        Request
            .Builder()
            .url(url)
            .headers(headers.toHeaders())
            .apply(block)
            .build()

    private fun parseFileInfo(response: Response): RemoteFileInfo {
        val etag = response.header(HEADER_ETAG)
        val lastModified = response.header(HEADER_LAST_MODIFIED)
        val contentType = response.header(HEADER_CONTENT_TYPE)
        val contentDisposition = response.header(HEADER_CONTENT_DISPOSITION)
        val acceptsRanges =
            response.header(HEADER_ACCEPT_RANGES) == BYTES_UNIT ||
                response.code == HTTP_PARTIAL
        val total =
            if (response.code == HTTP_PARTIAL) {
                totalFromContentRange(response.header(HEADER_CONTENT_RANGE))
            } else {
                response.header(HEADER_CONTENT_LENGTH)?.toLongOrNull() ?: DownloadProgress.UNKNOWN
            }
        return RemoteFileInfo(
            totalBytes = total,
            acceptsRanges = acceptsRanges,
            etag = etag,
            lastModified = lastModified,
            contentType = contentType,
            contentDisposition = contentDisposition,
        )
    }

    private fun totalFromContentRange(contentRange: String?): Long {
        val total = contentRange?.substringAfter('/', "")?.trim()
        return total?.toLongOrNull() ?: DownloadProgress.UNKNOWN
    }

    private companion object {
        const val HEADER_RANGE = "Range"
        const val HEADER_IF_RANGE = "If-Range"
        const val HEADER_ETAG = "ETag"
        const val HEADER_LAST_MODIFIED = "Last-Modified"
        const val HEADER_CONTENT_RANGE = "Content-Range"
        const val HEADER_CONTENT_LENGTH = "Content-Length"
        const val HEADER_CONTENT_TYPE = "Content-Type"
        const val HEADER_CONTENT_DISPOSITION = "Content-Disposition"
        const val HEADER_ACCEPT_RANGES = "Accept-Ranges"
        const val BYTES_UNIT = "bytes"
        const val HTTP_PARTIAL = 206
    }
}
