package io.github.alirezajavan10.downpour.api

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
)
