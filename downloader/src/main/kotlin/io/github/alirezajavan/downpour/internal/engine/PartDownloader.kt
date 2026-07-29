package io.github.alirezajavan.downpour.internal.engine

import io.github.alirezajavan.downpour.api.ChunkChecksum
import io.github.alirezajavan.downpour.api.DownloadDestination
import io.github.alirezajavan.downpour.api.DownloadError
import io.github.alirezajavan.downpour.internal.network.HttpDownloadDataSource
import io.github.alirezajavan.downpour.internal.util.HexUtils.toHex
import io.github.alirezajavan.downpour.internal.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

internal class PartDownloader(
    private val dataSource: HttpDownloadDataSource,
    private val fileStore: FileStore,
    private val logger: Logger,
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,
) {
    suspend fun download(context: PartContext) {
        val resolved = resolveRange(context.part)
        logger.d("Starting part ${context.part.partId} for ${context.url} (Resolved Range: ${resolved.rangeStart}-${resolved.rangeEnd})")
        val response =
            try {
                dataSource.open(
                    url = context.url,
                    headers = context.headers,
                    rangeStart = resolved.rangeStart,
                    rangeEnd = resolved.rangeEnd,
                    ifRange = context.ifRange,
                )
            } catch (t: Throwable) {
                logger.e("Part ${context.part.partId} failed to open connection for ${context.url}", t)
                throw t
            }
        // Close the response if this coroutine is cancelled, so a blocking socket read (or OkHttp's
        // own connection-retry loop) unblocks promptly on pause/cancel instead of waiting for a
        // timeout. dispose() removes the handler on the normal path.
        val cancelHandle = currentCoroutineContext().job.invokeOnCompletion { runCatching { response.close() } }
        try {
            response.use { writeBody(it, resolved, context) }
            logger.d("Part ${context.part.partId} finished (last offset: ${context.partOffset.get()})")
        } catch (t: Throwable) {
            logger.e(
                "Part ${context.part.partId} failed for ${context.url} " +
                    "(bytes written this attempt: ${context.partOffset.get() - resolved.writePosition})",
                t,
            )
            throw t
        } finally {
            cancelHandle.dispose()
        }
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
            if (!response.isSuccessful) {
                logger.w("Part ${context.part.partId} non-range request failed with ${response.code}")
                throw response.toHttpError()
            }
            return resolved
        }
        if (response.code == HTTP_PARTIAL) return resolved
        if (!response.isSuccessful) {
            logger.w(
                "Part ${context.part.partId} range request failed with ${response.code} " +
                    "(Retry-After=${response.header("Retry-After")}, multiConnection=${context.isMultiConnection})",
            )
            throw response.toHttpError()
        }
        logger.w(
            "Part ${context.part.partId} expected 206 but got ${response.code}: " +
                "server stopped honoring range requests (multiConnection=${context.isMultiConnection})",
        )
        if (context.isMultiConnection) {
            throw DownloadError.ContentValidation("Server stopped honoring range requests")
        }
        context.progress.addAndGet(-context.part.downloaded)
        return resolved.copy(writePosition = 0)
    }

    private fun Response.toHttpError(): DownloadError.Http {
        val retryAfter = header("Retry-After")?.toLongOrNull()
        return DownloadError.Http(code, retryAfter)
    }

    private suspend fun pump(
        stream: InputStream,
        startPosition: Long,
        context: PartContext,
    ) {
        val chunkConfig = context.chunkChecksum
        val digest = chunkConfig?.let { MessageDigest.getInstance(it.algorithm.javaName) }
        var bytesInCurrentChunk = 0L
        var isVerifying = chunkConfig != null && (startPosition % chunkConfig.chunkSize == 0L)
        var chunkOffset =
            if (chunkConfig != null) {
                (startPosition / chunkConfig.chunkSize) * chunkConfig.chunkSize
            } else {
                0L
            }

        fileStore.openWritable(context.destination).use { sink ->
            sink.seek(startPosition)
            val buffer = ByteArray(bufferSize)
            var absoluteOffset = startPosition
            var bytesSinceSpaceCheck = 0L
            while (true) {
                currentCoroutineContext().ensureActive()

                if (bytesSinceSpaceCheck >= SPACE_CHECK_INTERVAL) {
                    verifySpace(context)
                    bytesSinceSpaceCheck = 0
                }

                val read = readChunk(stream, buffer)
                if (read == END_OF_STREAM) {
                    if (isVerifying && digest != null && bytesInCurrentChunk > 0) {
                        verifyChunk(digest, chunkOffset, chunkConfig)
                    }
                    break
                }

                sink.write(buffer, 0, read)

                if (digest != null && chunkConfig != null) {
                    var bufferOffset = 0
                    var remainingRead = read
                    while (remainingRead > 0) {
                        if (isVerifying) {
                            val spaceInChunk = chunkConfig.chunkSize - bytesInCurrentChunk
                            val toProcess = remainingRead.toLong().coerceAtMost(spaceInChunk).toInt()
                            digest.update(buffer, bufferOffset, toProcess)
                            bytesInCurrentChunk += toProcess
                            bufferOffset += toProcess
                            remainingRead -= toProcess
                            absoluteOffset += toProcess

                            if (bytesInCurrentChunk == chunkConfig.chunkSize) {
                                verifyChunk(digest, chunkOffset, chunkConfig)
                                digest.reset()
                                bytesInCurrentChunk = 0
                                chunkOffset += chunkConfig.chunkSize
                            }
                        } else {
                            val nextBoundary =
                                ((absoluteOffset / chunkConfig.chunkSize) + 1) * chunkConfig.chunkSize
                            val bytesToBoundary = (nextBoundary - absoluteOffset).toInt()

                            if (remainingRead >= bytesToBoundary) {
                                isVerifying = true
                                bufferOffset += bytesToBoundary
                                remainingRead -= bytesToBoundary
                                absoluteOffset += bytesToBoundary
                                digest.reset()
                                bytesInCurrentChunk = 0
                                chunkOffset = nextBoundary
                            } else {
                                absoluteOffset += remainingRead
                                remainingRead = 0
                            }
                        }
                    }
                } else {
                    absoluteOffset += read
                }

                bytesSinceSpaceCheck += read.toLong()
                context.progress.addAndGet(read.toLong())
                context.partOffset.set(absoluteOffset)
                throttle(context, read)
            }
        }
    }

    private fun verifyChunk(
        digest: MessageDigest,
        offset: Long,
        config: ChunkChecksum,
    ) {
        val expected = config.checksums[offset] ?: return
        val actual = digest.digest().toHex()
        if (!actual.equals(expected, ignoreCase = true)) {
            throw DownloadError.ContentValidation("Chunk corruption detected at offset $offset")
        }
    }

    private fun verifySpace(context: PartContext) {
        val usable = fileStore.usableSpaceFor(context.destination)
        if (usable < MIN_SAFE_STORAGE_BYTES) {
            throw DownloadError.InsufficientStorage(MIN_SAFE_STORAGE_BYTES, usable)
        }
    }

    private suspend fun throttle(
        context: PartContext,
        bytes: Int,
    ) {
        context.rateLimiters.forEach { it.acquire(bytes) }
    }

    private suspend fun readChunk(
        stream: InputStream,
        buffer: ByteArray,
    ): Int =
        try {
            stream.read(buffer)
        } catch (io: IOException) {
            currentCoroutineContext().ensureActive()
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
        const val SPACE_CHECK_INTERVAL = 5L * 1024 * 1024 // 5MB
        const val MIN_SAFE_STORAGE_BYTES = 100L * 1024 * 1024 // 100MB
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
    val chunkChecksum: ChunkChecksum? = null,
)
