package io.github.alirezajavan.downpour.internal.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.alirezajavan.downpour.api.DownloadItem
import io.github.alirezajavan.downpour.internal.di.DownloaderGraph
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal class DownloadService : LifecycleService() {
    private val graph by lazy { DownloaderGraph.getInstance(applicationContext) }
    private var isObserving = false

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }
        handleAction(intent)
        startForegroundWith(emptyList())
        observeDownloads()
        return START_NOT_STICKY
    }

    private fun handleAction(intent: Intent?) {
        val action = intent?.action ?: return
        val id = intent.getStringExtra(EXTRA_ID)
        lifecycleScope.launch {
            when (action) {
                ACTION_PAUSE -> id?.let { graph.downloadManager.pause(it) }
                ACTION_CANCEL -> id?.let { graph.downloadManager.cancel(it) }
                ACTION_PAUSE_ALL -> graph.downloadManager.pauseAll()
                ACTION_CANCEL_ALL -> graph.downloadManager.cancelAll()
            }
        }
    }

    private fun observeDownloads() {
        if (isObserving) return
        isObserving = true
        lifecycleScope.launch {
            graph.repository
                .observeAllItems()
                .distinctUntilChanged()
                .collect { items -> onItemsChanged(items.filter { it.state.isActive }) }
        }
    }

    private fun onItemsChanged(active: List<DownloadItem>) {
        if (active.isEmpty()) {
            stopForegroundAndSelf()
        } else {
            graph.notificationManager.notify(NOTIFICATION_ID, graph.notificationFactory.build(active))
        }
    }

    private fun startForegroundWith(active: List<DownloadItem>) {
        graph.notificationFactory.ensureChannel()
        val notification = graph.notificationFactory.build(active)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType())
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    internal companion object {
        private const val NOTIFICATION_ID = 0x44504D
        const val ACTION_STOP = "io.github.alirezajavan.downpour.action.STOP"
        const val ACTION_PAUSE = "io.github.alirezajavan.downpour.action.PAUSE"
        const val ACTION_CANCEL = "io.github.alirezajavan.downpour.action.CANCEL"
        const val ACTION_PAUSE_ALL = "io.github.alirezajavan.downpour.action.PAUSE_ALL"
        const val ACTION_CANCEL_ALL = "io.github.alirezajavan.downpour.action.CANCEL_ALL"
        const val EXTRA_ID = "io.github.alirezajavan.downpour.extra.ID"

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
