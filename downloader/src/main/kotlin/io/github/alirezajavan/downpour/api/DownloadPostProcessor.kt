package io.github.alirezajavan.downpour.api

public fun interface DownloadPostProcessor {
    public suspend fun process(item: DownloadItem)
}
