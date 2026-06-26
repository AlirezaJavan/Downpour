package io.github.alirezajavan10.downpour.internal.network

import io.github.alirezajavan10.downpour.api.DownloadProgress

internal data class RemoteFileInfo(
    val totalBytes: Long,
    val acceptsRanges: Boolean,
    val etag: String?,
    val lastModified: String?,
    val contentType: String?,
    val contentDisposition: String?,
) {
    val hasKnownSize: Boolean
        get() = totalBytes > DownloadProgress.UNKNOWN
}
