package io.github.alirezajavan.downpour.internal.di

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import io.github.alirezajavan.downpour.api.DownloadManager
import io.github.alirezajavan.downpour.api.DownloadManagerConfig
import io.github.alirezajavan.downpour.internal.DefaultDownloadManager
import io.github.alirezajavan.downpour.internal.DownloadEventDispatcher
import io.github.alirezajavan.downpour.internal.data.DownloadRepository
import io.github.alirezajavan.downpour.internal.data.db.DownloadDatabase
import io.github.alirezajavan.downpour.internal.device.DeviceStateMonitor
import io.github.alirezajavan.downpour.internal.engine.AndroidFileStore
import io.github.alirezajavan.downpour.internal.engine.DownloadEngine
import io.github.alirezajavan.downpour.internal.engine.DownloadPlanner
import io.github.alirezajavan.downpour.internal.engine.DownloadTask
import io.github.alirezajavan.downpour.internal.engine.DownloadTaskRunner
import io.github.alirezajavan.downpour.internal.engine.PartDownloader
import io.github.alirezajavan.downpour.internal.engine.RateLimiter
import io.github.alirezajavan.downpour.internal.network.AdaptiveDns
import io.github.alirezajavan.downpour.internal.network.HttpDownloadDataSource
import io.github.alirezajavan.downpour.internal.network.Ipv6FailureListener
import io.github.alirezajavan.downpour.internal.network.Ipv6HealthTracker
import io.github.alirezajavan.downpour.internal.network.NetworkMonitor
import io.github.alirezajavan.downpour.internal.service.CompletionNotificationListener
import io.github.alirezajavan.downpour.internal.service.DownloadNotificationFactory
import io.github.alirezajavan.downpour.internal.service.ForegroundServiceController
import io.github.alirezajavan.downpour.internal.util.AndroidLogger
import io.github.alirezajavan.downpour.internal.util.DownloadLoggerAdapter
import io.github.alirezajavan.downpour.internal.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal class DownloaderGraph private constructor(
    context: Context,
    config: DownloadManagerConfig,
) {
    private val appContext = context.applicationContext
    private val ioDispatcher = config.ioDispatcher
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val logger: Logger =
        config.logger?.let { DownloadLoggerAdapter(it) } ?: AndroidLogger(config.verbose)

    private val database =
        Room
            .databaseBuilder(
                appContext,
                DownloadDatabase::class.java,
                DownloadDatabase.NAME,
            ).fallbackToDestructiveMigration(false)
            .build()

    val repository = DownloadRepository(database.downloadDao())

    // Starts pre-tripped when the user has explicitly opted into preferIpv4; otherwise dual-stack
    // is tried first and this flips automatically on the first observed IPv6 connect failure.
    private val ipv6HealthTracker = Ipv6HealthTracker(startTripped = config.preferIpv4)

    private val httpClient =
        (config.okHttpClient ?: OkHttpClient())
            .newBuilder()
            .apply {
                if (config.okHttpClient == null) {
                    // We own this client outright, so it's safe to install our own Dns/EventListener
                    // for automatic IPv6-failure detection. For a caller-supplied OkHttpClient we
                    // don't clobber their Dns/EventListener with this opportunistic behavior -- only
                    // the explicit preferIpv4 opt-in below applies in that case.
                    dns(AdaptiveDns(ipv6HealthTracker))
                    eventListener(Ipv6FailureListener(ipv6HealthTracker))
                } else if (config.preferIpv4) {
                    dns(AdaptiveDns(ipv6HealthTracker))
                }
                config.proxy?.let { proxy(it) }
                config.cookieJar?.let { cookieJar(it) }
            }.connectTimeout(config.connectTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .fastFallback(true)
            .build()

    private val dataSource = HttpDownloadDataSource(httpClient, ioDispatcher, logger, config.headerProvider)
    private val fileStore = AndroidFileStore(appContext)
    private val planner = DownloadPlanner(repository, config, logger)
    private val partDownloader = PartDownloader(dataSource, fileStore, logger)
    private val globalRateLimiter = RateLimiter(config.maxBytesPerSecond)

    val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val notificationFactory = DownloadNotificationFactory(appContext, config.notification)

    private val serviceController =
        ForegroundServiceController(appContext, config.notification.enabled)

    private val networkMonitor = NetworkMonitor(appContext)

    private val deviceStateMonitor = DeviceStateMonitor(appContext)

    private val scheduler =
        object : io.github.alirezajavan.downpour.internal.engine.DownloadScheduler {
            override fun schedule(delayMillis: Long) {
                if (delayMillis == 0L) {
                    io.github.alirezajavan.downpour.internal.work.DownloadRecoveryWorker
                        .schedule(appContext)
                } else {
                    io.github.alirezajavan.downpour.internal.work.DownloadWakeupReceiver
                        .schedule(appContext, delayMillis)
                }
            }
        }

    // Shared by every task instance so destination resolution across concurrent downloads is
    // serialized (prevents two same-URL downloads from claiming the same file).
    private val destinationMutex = Mutex()

    private val taskFactory: () -> DownloadTaskRunner = {
        DownloadTask(
            dataSource = dataSource,
            planner = planner,
            partDownloader = partDownloader,
            repository = repository,
            config = config,
            globalRateLimiter = globalRateLimiter,
            ioDispatcher = ioDispatcher,
            fileStore = fileStore,
            logger = logger,
            destinationMutex = destinationMutex,
        )
    }

    val engine =
        DownloadEngine(
            scope = scope,
            repository = repository,
            taskFactory = taskFactory,
            config = config,
            serviceController = serviceController,
            networkMonitor = networkMonitor,
            deviceStateMonitor = deviceStateMonitor,
            scheduler = scheduler,
            fileStore = fileStore,
            logger = logger,
        )

    private val eventDispatcher =
        DownloadEventDispatcher(scope, repository, config.listeners).apply {
            if (config.notification.enabled && config.notification.showCompletionNotification) {
                add(CompletionNotificationListener(notificationManager, notificationFactory))
            }
        }

    val downloadManager: DownloadManager =
        DefaultDownloadManager(
            repository = repository,
            engine = engine,
            scope = scope,
            config = config,
            logger = logger,
            eventDispatcher = eventDispatcher,
        )

    init {
        scope.launch { engine.recover() }
    }

    /**
     * Tears this graph down: cancelling [scope] stops every in-flight part job and, via
     * `awaitClose`, unregisters the network/battery callbacks those jobs were collecting.
     * In-flight downloads aren't lost -- their bytes are flushed to disk per chunk, and rows left
     * in [io.github.alirezajavan.downpour.internal.data.DownloadStatus.RUNNING] are requeued by the
     * next graph's `engine.recover()`.
     */
    private fun shutdown() {
        scope.cancel()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
        database.close()
    }

    companion object {
        @Volatile
        private var instance: DownloaderGraph? = null

        fun getInstance(
            context: Context,
            config: DownloadManagerConfig = DownloadManagerConfig(),
        ): DownloaderGraph {
            val current = instance
            if (current != null) {
                return current
            }
            return synchronized(this) {
                instance ?: DownloaderGraph(context, config).also { instance = it }
            }
        }

        /** Shuts down the current instance (if any) and replaces it with one built from [config]. */
        fun reconfigure(
            context: Context,
            config: DownloadManagerConfig,
        ): DownloaderGraph =
            synchronized(this) {
                instance?.shutdown()
                DownloaderGraph(context, config).also { instance = it }
            }
    }
}
