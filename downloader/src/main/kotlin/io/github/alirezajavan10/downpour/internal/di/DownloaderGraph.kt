package io.github.alirezajavan10.downpour.internal.di

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import io.github.alirezajavan10.downpour.api.DownloadManager
import io.github.alirezajavan10.downpour.api.DownloadManagerConfig
import io.github.alirezajavan10.downpour.internal.DefaultDownloadManager
import io.github.alirezajavan10.downpour.internal.data.DownloadRepository
import io.github.alirezajavan10.downpour.internal.data.db.DownloadDatabase
import io.github.alirezajavan10.downpour.internal.engine.AndroidFileStore
import io.github.alirezajavan10.downpour.internal.engine.DownloadEngine
import io.github.alirezajavan10.downpour.internal.engine.DownloadPlanner
import io.github.alirezajavan10.downpour.internal.engine.DownloadTask
import io.github.alirezajavan10.downpour.internal.engine.PartDownloader
import io.github.alirezajavan10.downpour.internal.engine.RateLimiter
import io.github.alirezajavan10.downpour.internal.network.HttpDownloadDataSource
import io.github.alirezajavan10.downpour.internal.network.NetworkMonitor
import io.github.alirezajavan10.downpour.internal.service.DownloadNotificationFactory
import io.github.alirezajavan10.downpour.internal.service.ForegroundServiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal class DownloaderGraph private constructor(
    context: Context,
    config: DownloadManagerConfig,
) {
    private val appContext = context.applicationContext
    private val ioDispatcher = Dispatchers.IO
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

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
            .connectTimeout(config.connectTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()

    private val dataSource = HttpDownloadDataSource(httpClient, ioDispatcher)
    private val fileStore = AndroidFileStore(appContext)
    private val planner = DownloadPlanner(repository, config)
    private val partDownloader = PartDownloader(dataSource, fileStore)
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
        )

    val downloadManager: DownloadManager = DefaultDownloadManager(repository, engine, scope, config)

    init {
        scope.launch { engine.recover() }
    }

    companion object {
        @Volatile
        private var instance: DownloaderGraph? = null

        fun getInstance(
            context: Context,
            config: DownloadManagerConfig = DownloadManagerConfig(),
        ): DownloaderGraph =
            instance ?: synchronized(this) {
                instance ?: DownloaderGraph(context, config).also { instance = it }
            }
    }
}
