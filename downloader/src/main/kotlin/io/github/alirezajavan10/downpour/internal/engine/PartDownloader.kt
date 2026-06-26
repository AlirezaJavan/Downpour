package io.github.alirezajavan10.downpour.internal.engine

import io.github.alirezajavan10.downpour.api.DownloadDestination
import io.github.alirezajavan10.downpour.api.DownloadError
import io.github.alirezajavan10.downpour.internal.network.HttpDownloadDataSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

internal class PartDownloader(
    private val dataSource: HttpDownloadDataSource,
    private val fileStore: FileStore,
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,
) {
    suspend fun download(context: PartContext) {
        val resolved = resolveRange(context.part)
        val response =
            dataSource.open(
                url = context.url,
                headers = context.headers,
                rangeStart = resolved.rangeStart,
                rangeEnd = resolved.rangeEnd,
                ifRange = context.ifRange,
            )
        response.use { writeBody(it, resolved, context) }
    }

    private fun resolveRange(part: PartPlan): ResolvedRange {
        if (part.nextByte == 0L && part.isOpenEnded) {
            return ResolvedRange(rangeStart = null, rangeEnd = null, writePosition = 0)
        }
        return ResolvedRange(
            rangeStart = part.nextByte,
            rangeEnd = if (part.isOpenEnded) null else part.end,
            writePosition = part.nextByte,
        )
    }

    private suspend fun writeBody(
        response: Response,
        resolved: ResolvedRange,
        context: PartContext,
    ) {
        val effective = adjustForServerResponse(response, resolved, context)
        val body = response.body
        pump(body.byteStream(), effective.writePosition, context)
    }

    private fun adjustForServerResponse(
        response: Response,
        resolved: ResolvedRange,
        context: PartContext,
    ): ResolvedRange {
        if (resolved.rangeStart == null) {
            if (!response.isSuccessful) throw DownloadError.Http(response.code)
            return resolved
        }
        if (response.code == HTTP_PARTIAL) return resolved
        if (!response.isSuccessful) throw DownloadError.Http(response.code)
        if (context.isMultiConnection) {
            throw DownloadError.ContentValidation("Server stopped honoring range requests")
        }
        context.progress.addAndGet(-context.part.downloaded)
        return resolved.copy(writePosition = 0)
    }

    private suspend fun pump(
        stream: java.io.InputStream,
        startPosition: Long,
        context: PartContext,
    ) {
        fileStore.openWritable(context.destination).use { sink ->
            sink.seek(startPosition)
            val buffer = ByteArray(bufferSize)
            var absoluteOffset = startPosition
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = readChunk(stream, buffer)
                if (read == END_OF_STREAM) break
                sink.write(buffer, 0, read)
                absoluteOffset += read
                context.progress.addAndGet(read.toLong())
                context.partOffset.set(absoluteOffset)
                throttle(context, read)
            }
        }
    }

    private suspend fun throttle(
        context: PartContext,
        bytes: Int,
    ) {
        context.rateLimiters.forEach { it.acquire(bytes) }
    }

    private fun readChunk(
        stream: java.io.InputStream,
        buffer: ByteArray,
    ): Int =
        try {
            stream.read(buffer)
        } catch (io: IOException) {
            throw DownloadError.Connection(io)
        }

    private data class ResolvedRange(
        val rangeStart: Long?,
        val rangeEnd: Long?,
        val writePosition: Long,
    )

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
        const val HTTP_PARTIAL = 206
        const val END_OF_STREAM = -1
    }
}

internal data class PartContext(
    val url: String,
    val headers: Map<String, String>,
    val part: PartPlan,
    val ifRange: String?,
    val destination: DownloadDestination,
    val isMultiConnection: Boolean,
    val progress: AtomicLong,
    val partOffset: AtomicLong,
    val rateLimiters: List<RateLimiter>,
)
