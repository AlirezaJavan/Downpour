package io.github.alirezajavan10.downpour.api

public sealed class DownloadError(
    message: String?,
    cause: Throwable?,
) : Exception(message, cause) {
    public class Connection(
        cause: Throwable?,
    ) : DownloadError("Network connection failed", cause)

    public class Timeout(
        cause: Throwable?,
    ) : DownloadError("The request timed out", cause)

    public class Http(
        public val statusCode: Int,
        public val retryAfterSeconds: Long? = null,
    ) : DownloadError("Unexpected HTTP status code: $statusCode", null)

    public class InsufficientStorage(
        requiredBytes: Long,
        availableBytes: Long,
    ) : DownloadError(
            "Not enough free space: required=$requiredBytes available=$availableBytes",
            null,
        )

    public class FileAlreadyExists(
        filePath: String,
    ) : DownloadError("File already exists: $filePath", null)

    public class Storage(
        message: String,
        cause: Throwable?,
    ) : DownloadError(message, cause)

    public class ContentValidation(
        message: String,
    ) : DownloadError(message, null)

    public class Unknown(
        cause: Throwable?,
    ) : DownloadError("Unknown download error", cause)

    public val isRetryable: Boolean
        get() =
            when (this) {
                is Connection, is Timeout, is Unknown -> true
                is Http -> statusCode == TOO_MANY_REQUESTS || statusCode >= INTERNAL_SERVER_ERROR
                is InsufficientStorage, is Storage, is ContentValidation, is FileAlreadyExists -> false
            }

    private companion object {
        const val TOO_MANY_REQUESTS = 429
        const val INTERNAL_SERVER_ERROR = 500
    }
}
