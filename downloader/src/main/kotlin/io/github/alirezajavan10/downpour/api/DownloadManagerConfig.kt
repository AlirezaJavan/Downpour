package io.github.alirezajavan10.downpour.api

import okhttp3.OkHttpClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

public data class DownloadManagerConfig(
    val maxConcurrentDownloads: Int = DEFAULT_MAX_CONCURRENT,
    val defaultRetryPolicy: RetryPolicy = RetryPolicy(),
    val progressUpdateInterval: Duration = DEFAULT_PROGRESS_INTERVAL,
    val connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    val readTimeout: Duration = DEFAULT_READ_TIMEOUT,
    val minSizeForMultiConnection: Long = DEFAULT_MIN_MULTI_CONNECTION_SIZE,
    val maxBytesPerSecond: Long = UNLIMITED,
    val okHttpClient: OkHttpClient? = null,
    val interceptors: List<DownloadInterceptor> = emptyList(),
    val workerFactory: DownloadWorkerFactory = DefaultDownloadWorkerFactory,
    val notification: NotificationConfig = NotificationConfig(),
) {
    init {
        require(maxConcurrentDownloads >= 1) { "maxConcurrentDownloads must be >= 1" }
        require(maxBytesPerSecond >= 0) { "maxBytesPerSecond must be >= 0 (0 = unlimited)" }
    }

    public companion object {
        public const val UNLIMITED: Long = 0L
        public const val DEFAULT_MAX_CONCURRENT: Int = 3
        public const val DEFAULT_MIN_MULTI_CONNECTION_SIZE: Long = 5L * 1024 * 1024
        public val DEFAULT_PROGRESS_INTERVAL: Duration = 500.milliseconds
        public val DEFAULT_CONNECT_TIMEOUT: Duration = 30.seconds
        public val DEFAULT_READ_TIMEOUT: Duration = 30.seconds
    }
}

public interface DownloadWorkerFactory {
    public fun create(workerClassName: String): DownloadWorker?
}

public object DefaultDownloadWorkerFactory : DownloadWorkerFactory {
    override fun create(workerClassName: String): DownloadWorker? = null
}
