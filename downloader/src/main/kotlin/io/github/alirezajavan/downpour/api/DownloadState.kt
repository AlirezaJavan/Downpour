package io.github.alirezajavan.downpour.api

public sealed interface DownloadState {
    public data object Queued : DownloadState

    public data class Running(
        val progress: DownloadProgress,
    ) : DownloadState

    public data class Paused(
        val progress: DownloadProgress,
    ) : DownloadState

    public data class Completed(
        val destination: DownloadDestination,
        val totalBytes: Long,
    ) : DownloadState {
        @Deprecated("Use destination instead")
        public val filePath: String
            get() = (destination as? DownloadDestination.File)?.path ?: ""
    }

    public data class Failed(
        val error: DownloadError,
    ) : DownloadState

    public data object WaitingForNetwork : DownloadState

    public data object Cancelled : DownloadState

    public val isTerminal: Boolean
        get() = this is Completed || this is Cancelled || (this is Failed && !error.isRetryable)

    public val isActive: Boolean
        get() = this is Queued || this is Running
}
