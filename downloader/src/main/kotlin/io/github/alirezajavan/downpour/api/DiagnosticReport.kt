package io.github.alirezajavan.downpour.api

public data class PartProgress(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    val currentOffset: Long,
) {
    public val downloadedBytes: Long
        get() = (currentOffset - startByte).coerceAtLeast(0)

    public val totalBytes: Long
        get() = (endByte - startByte + 1).coerceAtLeast(0)

    public val progress: Float
        get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
}

public data class DiagnosticReport(
    val id: String,
    val url: String,
    val state: DownloadState,
    val retryCount: Int,
    val lastError: DownloadError?,
    val isResumeSupported: Boolean,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val etag: String?,
    val lastModified: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val parts: List<PartProgress> = emptyList(),
)
