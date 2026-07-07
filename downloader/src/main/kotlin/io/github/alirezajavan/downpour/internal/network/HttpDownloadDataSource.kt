package io.github.alirezajavan.downpour.internal.network

import io.github.alirezajavan.downpour.api.DownloadError
import io.github.alirezajavan.downpour.api.DownloadProgress
import io.github.alirezajavan.downpour.api.HeaderProvider
import io.github.alirezajavan.downpour.internal.util.Logger
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
    private val logger: Logger,
    private val headerProvider: HeaderProvider? = null,
) {
    suspend fun probe(
        url: String,
        headers: Map<String, String>,
    ): RemoteFileInfo =
        withContext(ioDispatcher) {
            logger.d("Probing URL: $url")
            val headRequest = buildRequest(url, headers) { head() }
            val headResponse =
                try {
                    execute(headRequest)
                } catch (e: Exception) {
                    logger.w("HEAD request failed for $url. Trying GET fallback...", e)
                    null
                }

            headResponse?.use { response ->
                if (response.isSuccessful) {
                    val info = parseFileInfo(response)
                    if (info.totalBytes != DownloadProgress.UNKNOWN) {
                        logger.d("Probe (HEAD) successful for $url: $info")
                        return@withContext info
                    }
                } else {
                    logger.d("HEAD request unsuccessful: ${response.code}")
                }
            }

            logger.d("Falling back to GET (Range: 0-0) for probe: $url")
            val getRequest =
                buildRequest(url, headers) {
                    header(HEADER_RANGE, "$BYTES_UNIT=0-0")
                }
            execute(getRequest).use { response ->
                if (!response.isSuccessful) {
                    logger.e("Probe (GET) failed with ${response.code} for $url")
                    throw response.toHttpError()
                }
                val info = parseFileInfo(response)
                logger.d("Probe (GET) successful for $url: $info")
                info
            }
        }

    fun open(
        url: String,
        headers: Map<String, String>,
        rangeStart: Long?,
        rangeEnd: Long?,
        ifRange: String?,
    ): Response {
        logger.d("Opening connection for $url (Range: $rangeStart-$rangeEnd, If-Range: $ifRange)")
        val request =
            buildRequest(url, headers) {
                if (rangeStart != null) {
                    val suffix = rangeEnd?.toString().orEmpty()
                    header(HEADER_RANGE, "$BYTES_UNIT=$rangeStart-$suffix")
                    ifRange?.let { header(HEADER_IF_RANGE, it) }
                }
            }
        val response = execute(request)
        if (!response.isSuccessful) {
            logger.w(
                "Non-2xx response opening $url (Range: $rangeStart-$rangeEnd): " +
                    "${response.code} ${response.message}, Retry-After=${response.header("Retry-After")}",
            )
        } else {
            logger.d("Opened $url with status ${response.code} (Range: $rangeStart-$rangeEnd)")
        }
        return response
    }

    private fun execute(request: Request): Response =
        try {
            client.newCall(request).execute()
        } catch (timeout: SocketTimeoutException) {
            logger.e("Timeout executing request: ${request.url}", timeout)
            throw DownloadError.Timeout(timeout)
        } catch (io: IOException) {
            logger.e("Connection error executing request: ${request.url}", io)
            throw DownloadError.Connection(io)
        }

    private fun Response.toHttpError(): DownloadError.Http {
        val retryAfter = header("Retry-After")?.toLongOrNull()
        return DownloadError.Http(code, retryAfter)
    }

    private fun buildRequest(
        url: String,
        headers: Map<String, String>,
        block: Request.Builder.() -> Unit,
    ): Request =
        Request
            .Builder()
            .url(url)
            .headers(effectiveHeaders(url, headers).toHeaders())
            .apply {
                if (!headers.containsKey(HEADER_USER_AGENT)) {
                    header(HEADER_USER_AGENT, DEFAULT_USER_AGENT)
                }
            }.apply(block)
            .build()

    // Provider headers overlay the per-request ones so a fresh value (e.g. a refreshed auth token)
    // wins on every attempt, including resumes after the original token expired.
    private fun effectiveHeaders(
        url: String,
        headers: Map<String, String>,
    ): Map<String, String> = headerProvider?.let { headers + it.headers(url) } ?: headers

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
        const val HEADER_USER_AGENT = "User-Agent"
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
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
