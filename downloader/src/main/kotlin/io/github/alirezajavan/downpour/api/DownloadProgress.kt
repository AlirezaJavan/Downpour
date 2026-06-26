package io.github.alirezajavan.downpour.api

public data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val etaMillis: Long,
) {
    val isIndeterminate: Boolean
        get() = totalBytes <= UNKNOWN

    val fraction: Float
        get() = if (totalBytes <= 0) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)

    val percent: Int
        get() = (fraction * PERCENT_SCALE).toInt()

    public companion object {
        public const val UNKNOWN: Long = -1L
        private const val PERCENT_SCALE = 100

        public val EMPTY: DownloadProgress =
            DownloadProgress(
                downloadedBytes = 0,
                totalBytes = UNKNOWN,
                bytesPerSecond = 0,
                etaMillis = UNKNOWN,
            )
    }
}
