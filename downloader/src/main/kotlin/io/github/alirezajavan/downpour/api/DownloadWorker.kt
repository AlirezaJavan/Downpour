package io.github.alirezajavan.downpour.api

public interface DownloadWorker {
    public suspend fun process(item: DownloadItem)
}
