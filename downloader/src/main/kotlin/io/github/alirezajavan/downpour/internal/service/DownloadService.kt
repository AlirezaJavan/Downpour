package io.github.alirezajavan.downpour.internal.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.alirezajavan.downpour.api.DownloadItem
import io.github.alirezajavan.downpour.api.DownloadState
import io.github.alirezajavan.downpour.internal.di.DownloaderGraph
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class DownloadService : LifecycleService() {
    private val graph by lazy { DownloaderGraph.getInstance(applicationContext) }
    private var isObserving = false
    private var hasRenderedOngoing = false

    /**
     * True once this instance has dispatched an explicit user action (pause/resume/cancel from the
     * notification). When set, an emptied "ongoing" list is a real "everything is gone, stop now"
     * signal rather than the startup race we otherwise ignore.
     */
    private var dispatchedUserAction = false

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        // Always promote to foreground within the system grace window, BEFORE anything that suspends.
        startForegroundPlaceholder()

        if (intent?.action == ACTION_STOP) {
            removeAndStop()
            return START_NOT_STICKY
        }
        lifecycleScope.launch {
            // Await the action FIRST so the lifecycle observer's initial emission reflects the
            // resolved state. Otherwise a notification action that spawns a fresh instance (the
            // service stops itself while only paused) would render the stale pre-action state and
            // stopSelf() before the action even completes.
            dispatchAction(intent)
            observeDownloads()
        }
        return START_NOT_STICKY
    }

    private suspend fun dispatchAction(intent: Intent?) {
        val action = intent?.action ?: return
        val id = intent.getStringExtra(EXTRA_ID)
        dispatchedUserAction = true
        when (action) {
            ACTION_PAUSE -> id?.let { graph.downloadManager.pause(it) }
            ACTION_RESUME -> id?.let { graph.downloadManager.resume(it) }
            ACTION_CANCEL -> id?.let { graph.downloadManager.cancel(it) }
            ACTION_PAUSE_ALL -> graph.downloadManager.pauseAll()
            ACTION_RESUME_ALL -> graph.downloadManager.resumeAll()
            ACTION_CANCEL_ALL -> graph.downloadManager.cancelAll()
        }
    }

    private suspend fun observeDownloads() {
        if (isObserving) return
        isObserving = true
        graph.repository
            .observeAllItems()
            .map { items -> items.filter { it.state.isOngoing } }
            .distinctUntilChanged()
            .collect { render(it) }
    }

    /**
     * Drives the service/notification lifecycle from the actual download states:
     *  - any RUNNING  -> stay a foreground service, live progress notification.
     *  - only PAUSED/WAITING -> demote (detach the notification so it survives) and stop the service,
     *    leaving a persistent, resumable notification. This is what lets you resume from the shade
     *    after pausing instead of the whole service disappearing.
     *  - nothing ongoing -> remove the notification and stop.
     */
    private fun render(ongoing: List<DownloadItem>) {
        val anyRunning = ongoing.any { it.state is DownloadState.Running }
        when {
            anyRunning -> {
                hasRenderedOngoing = true
                startForegroundWith(ongoing)
            }

            ongoing.isNotEmpty() -> {
                hasRenderedOngoing = true
                graph.notificationManager.notify(NOTIFICATION_ID, graph.notificationFactory.build(ongoing))
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                stopSelf()
            }

            // Nothing ongoing: stop if we've actually shown work, or if we got here by handling a
            // user action (e.g. Cancel all) that legitimately emptied the list. Otherwise this is
            // the initial empty emission before any state has appeared (startup race) — ignore it.
            hasRenderedOngoing || dispatchedUserAction -> {
                removeAndStop()
            }
        }
    }

    private fun startForegroundPlaceholder() = startForegroundWith(emptyList())

    private fun startForegroundWith(ongoing: List<DownloadItem>) {
        graph.notificationFactory.ensureChannel()
        val notification = graph.notificationFactory.build(ongoing)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType())
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

    private fun removeAndStop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    internal companion object {
        private const val NOTIFICATION_ID = 0x44504D
        const val ACTION_STOP = "io.github.alirezajavan.downpour.action.STOP"
        const val ACTION_PAUSE = "io.github.alirezajavan.downpour.action.PAUSE"
        const val ACTION_RESUME = "io.github.alirezajavan.downpour.action.RESUME"
        const val ACTION_CANCEL = "io.github.alirezajavan.downpour.action.CANCEL"
        const val ACTION_PAUSE_ALL = "io.github.alirezajavan.downpour.action.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "io.github.alirezajavan.downpour.action.RESUME_ALL"
        const val ACTION_CANCEL_ALL = "io.github.alirezajavan.downpour.action.CANCEL_ALL"
        const val EXTRA_ID = "io.github.alirezajavan.downpour.extra.ID"

        /** States worth keeping a notification up for. */
        private val DownloadState.isOngoing: Boolean
            get() =
                this is DownloadState.Running ||
                    this is DownloadState.Paused ||
                    this is DownloadState.WaitingForNetwork

        fun start(context: Context) {
            startServiceCompat(context, Intent(context, DownloadService::class.java))
        }

        fun stop(context: Context) {
            startServiceCompat(context, Intent(context, DownloadService::class.java).setAction(ACTION_STOP))
        }

        private fun startServiceCompat(
            context: Context,
            intent: Intent,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
