package io.github.alirezajavan.downpour.api

public sealed interface DownloadDestination {
    public data class File(
        val path: String,
    ) : DownloadDestination

    public data class Uri(
        val uriString: String,
    ) : DownloadDestination
}
