package io.github.alirezajavan.downpour.internal.data

import io.github.alirezajavan.downpour.api.DownloadError

internal data class EncodedError(
    val type: Int,
    val message: String?,
    val httpCode: Int?,
)

internal object ErrorCodec {
    private const val CONNECTION = 0
    private const val TIMEOUT = 1
    private const val HTTP = 2
    private const val INSUFFICIENT_STORAGE = 3
    private const val STORAGE = 4
    private const val CONTENT_VALIDATION = 5
    private const val FILE_ALREADY_EXISTS = 6
    private const val UNKNOWN = 7

    fun encode(error: DownloadError): EncodedError =
        when (error) {
            is DownloadError.Connection -> {
                EncodedError(CONNECTION, error.message, null)
            }

            is DownloadError.Timeout -> {
                EncodedError(TIMEOUT, error.message, null)
            }

            is DownloadError.Http -> {
                EncodedError(HTTP, error.message, error.statusCode)
            }

            is DownloadError.InsufficientStorage -> {
                EncodedError(INSUFFICIENT_STORAGE, error.message, null)
            }

            is DownloadError.Storage -> {
                EncodedError(STORAGE, error.message, null)
            }

            is DownloadError.ContentValidation -> {
                EncodedError(CONTENT_VALIDATION, error.message, null)
            }

            is DownloadError.FileAlreadyExists -> {
                EncodedError(FILE_ALREADY_EXISTS, error.message, null)
            }

            is DownloadError.Unknown -> {
                EncodedError(UNKNOWN, error.message, null)
            }
        }

    fun decode(
        type: Int?,
        message: String?,
        httpCode: Int?,
    ): DownloadError =
        when (type) {
            CONNECTION -> {
                DownloadError.Connection(null)
            }

            TIMEOUT -> {
                DownloadError.Timeout(null)
            }

            HTTP -> {
                DownloadError.Http(httpCode ?: -1)
            }

            INSUFFICIENT_STORAGE -> {
                DownloadError.Storage(message ?: "Insufficient storage", null)
            }

            STORAGE -> {
                DownloadError.Storage(message ?: "Storage error", null)
            }

            CONTENT_VALIDATION -> {
                DownloadError.ContentValidation(message ?: "Content validation failed")
            }

            FILE_ALREADY_EXISTS -> {
                DownloadError.FileAlreadyExists(message ?: "")
            }

            else -> {
                DownloadError.Unknown(null)
            }
        }
}
