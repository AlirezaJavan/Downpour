package io.github.alirezajavan.downpour.api

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.net.Proxy
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
    val verbose: Boolean = false,
    val preferIpv4: Boolean = false,
    val filenameResolver: FilenameResolver = FilenameResolver.Default,
    val logger: DownloadLogger? = null,
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val postProcessors: List<DownloadPostProcessor> = emptyList(),
    val headerProvider: HeaderProvider? = null,
    val listeners: List<DownloadListener> = emptyList(),
    val expireCompletedAfter: Duration? = null,
    val proxy: Proxy? = null,
    val cookieJar: CookieJar? = null,
    val adaptiveConcurrency: Boolean = false,
    val minConnections: Int = DEFAULT_MIN_CONNECTIONS,
    val concurrencyReevaluationInterval: Duration = DEFAULT_REEVALUATION_INTERVAL,
    val duplicatePolicy: DuplicatePolicy = DuplicatePolicy.REUSE_EXISTING,
) {
    init {
        require(maxConcurrentDownloads >= 1) { "maxConcurrentDownloads must be >= 1" }
        require(maxBytesPerSecond >= 0) { "maxBytesPerSecond must be >= 0 (0 = unlimited)" }
        require(minConnections >= 1) { "minConnections must be >= 1" }
    }

    public companion object {
        public const val UNLIMITED: Long = 0L
        public const val DEFAULT_MAX_CONCURRENT: Int = 3
        public const val DEFAULT_MIN_MULTI_CONNECTION_SIZE: Long = 5L * 1024 * 1024
        public const val DEFAULT_MIN_CONNECTIONS: Int = 1
        public val DEFAULT_PROGRESS_INTERVAL: Duration = 500.milliseconds
        public val DEFAULT_CONNECT_TIMEOUT: Duration = 10.seconds
        public val DEFAULT_READ_TIMEOUT: Duration = 10.seconds
        public val DEFAULT_REEVALUATION_INTERVAL: Duration = 5.seconds
    }
}

public interface DownloadWorkerFactory {
    public fun create(workerClassName: String): DownloadWorker?
}

public object DefaultDownloadWorkerFactory : DownloadWorkerFactory {
    override fun create(workerClassName: String): DownloadWorker? = null
}
