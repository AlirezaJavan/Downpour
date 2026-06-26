package io.github.alirezajavan10.downpour.api

public interface DownloadInterceptor {
    public fun intercept(request: DownloadRequest): DownloadRequest
}
