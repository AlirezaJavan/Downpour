package io.github.alirezajavan.downpour.internal.network

import io.github.alirezajavan.downpour.api.DownloadProgress

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
