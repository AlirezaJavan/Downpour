package io.github.alirezajavan.downpour.api

public data class GroupProgress(
    val total: Int,
    val completed: Int,
    val failed: Int,
    val running: Int,
    val queued: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
) {
    val fraction: Float
        get() = if (totalBytes <= 0) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)

    val isComplete: Boolean
        get() = total > 0 && completed == total
}
