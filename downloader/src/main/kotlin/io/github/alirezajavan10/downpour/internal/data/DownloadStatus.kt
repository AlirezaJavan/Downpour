package io.github.alirezajavan10.downpour.internal.data

internal enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
    WAITING_FOR_NETWORK,
}
