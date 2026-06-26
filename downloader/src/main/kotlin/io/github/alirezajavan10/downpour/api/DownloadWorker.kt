package io.github.alirezajavan10.downpour.api

public interface DownloadWorker {
    public suspend fun process(item: DownloadItem)
}
