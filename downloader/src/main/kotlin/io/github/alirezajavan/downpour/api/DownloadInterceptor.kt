package io.github.alirezajavan.downpour.api

public interface DownloadInterceptor {
    public fun intercept(request: DownloadRequest): DownloadRequest
}
