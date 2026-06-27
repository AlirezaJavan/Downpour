package io.github.alirezajavan.downpour.internal.di

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import io.github.alirezajavan.downpour.api.DownloadManager
import io.github.alirezajavan.downpour.api.DownloadManagerConfig
import io.github.alirezajavan.downpour.internal.DefaultDownloadManager
import io.github.alirezajavan.downpour.internal.data.DownloadRepository
import io.github.alirezajavan.downpour.internal.data.db.DownloadDatabase
import io.github.alirezajavan.downpour.internal.engine.AndroidFileStore
import io.github.alirezajavan.downpour.internal.engine.DownloadEngine
import io.github.alirezajavan.downpour.internal.engine.DownloadPlanner
import io.github.alirezajavan.downpour.internal.engine.DownloadTask
import io.github.alirezajavan.downpour.internal.engine.PartDownloader
import io.github.alirezajavan.downpour.internal.engine.RateLimiter
import io.github.alirezajavan.downpour.internal.network.HttpDownloadDataSource
import io.github.alirezajavan.downpour.internal.network.NetworkMonitor
import io.github.alirezajavan.downpour.internal.service.DownloadNotificationFactory
import io.github.alirezajavan.downpour.internal.service.ForegroundServiceController
import io.github.alirezajavan.downpour.internal.util.AndroidLogger
import io.github.alirezajavan.downpour.internal.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.util.concurrent.TimeUnit

internal class DownloaderGraph private constructor(
    context: Context,
    config: DownloadManagerConfig,
) {
    private val appContext = context.applicationContext
    private val ioDispatcher = Dispatchers.IO
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val logger: Logger = AndroidLogger(config.verbose)

    private val database =
        Room
            .databaseBuilder(
                appContext,
                DownloadDatabase::class.java,
                DownloadDatabase.NAME,
            ).fallbackToDestructiveMigration(false)
            .build()

    val repository = DownloadRepository(database.downloadDao())

    private val httpClient =
        (config.okHttpClient ?: OkHttpClient())
            .newBuilder()
            .apply {
                if (config.preferIpv4) {
                    dns(IPv4OnlyDns)
                }
            }.connectTimeout(config.connectTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .fastFallback(true)
            .build()

    private val dataSource = HttpDownloadDataSource(httpClient, ioDispatcher, logger)
    private val fileStore = AndroidFileStore(appContext)
    private val planner = DownloadPlanner(repository, config)
    private val partDownloader = PartDownloader(dataSource, fileStore, logger)
    private val globalRateLimiter = RateLimiter(config.maxBytesPerSecond)

    val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val notificationFactory = DownloadNotificationFactory(appContext, config.notification)

    private val serviceController =
        ForegroundServiceController(appContext, config.notification.enabled)

    private val networkMonitor = NetworkMonitor(appContext)

    private val taskFactory: () -> DownloadTask = {
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
            fileStore = fileStore,
            logger = logger,
        )

    val downloadManager: DownloadManager =
        DefaultDownloadManager(
            repository = repository,
            engine = engine,
            scope = scope,
            config = config,
            logger = logger,
        )

    init {
        scope.launch { engine.recover() }
    }

    private object IPv4OnlyDns : Dns {
        override fun lookup(hostname: String) =
            Dns.SYSTEM.lookup(hostname).filter { it is Inet4Address }.ifEmpty {
                Dns.SYSTEM.lookup(hostname)
            }
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
                // If config changed, we might need to update internal state.
                // For simplicity in this fix, we just return the existing instance,
                // but in a real app, you should kill the process or handle dynamic config.
                return current
            }
            return synchronized(this) {
                instance ?: DownloaderGraph(context, config).also { instance = it }
            }
        }

        // Added for testing and debugging to allow fresh start with new config
        internal fun destroyInstance() {
            synchronized(this) {
                instance = null
            }
        }
    }
}
