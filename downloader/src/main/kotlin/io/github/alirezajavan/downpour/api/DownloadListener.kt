package io.github.alirezajavan.downpour.api

public fun interface DownloadListener {
    public fun onStateChanged(item: DownloadItem)
}
