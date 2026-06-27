package io.github.alirezajavan.downpour.internal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.alirezajavan.downpour.api.DownloadItem
import io.github.alirezajavan.downpour.api.DownloadProgress
import io.github.alirezajavan.downpour.api.DownloadState
import io.github.alirezajavan.downpour.api.NotificationConfig
import io.github.alirezajavan.downpour.internal.util.ByteFormatter

internal class DownloadNotificationFactory(
    private val context: Context,
    private val config: NotificationConfig,
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel =
            NotificationChannel(
                config.channelId,
                config.channelName,
                NotificationManager.IMPORTANCE_LOW,
            )
        notificationManager().createNotificationChannel(channel)
    }

    fun build(activeItems: List<DownloadItem>): Notification {
        val withProgress = activeItems.mapNotNull { item -> item.progress()?.let { item to it } }
        val anyRunning = activeItems.any { it.state is DownloadState.Running }
        val percent = aggregatePercent(withProgress.map { it.second })

        val builder =
            NotificationCompat
                .Builder(context, config.channelId)
                .setSmallIcon(config.smallIconRes)
                .setContentTitle(title(activeItems))
                .setContentText(text(activeItems, withProgress))
                .setOngoing(anyRunning)
                .setOnlyAlertOnce(true)
                .setProgress(MAX_PROGRESS, percent, withProgress.isEmpty())

        addActions(builder, activeItems)
        return builder.build()
    }

    private fun addActions(
        builder: NotificationCompat.Builder,
        items: List<DownloadItem>,
    ) {
        // Empty placeholder (the foreground-grace notification shown before state resolves): no
        // actions — otherwise it would show "Resume all"/"Cancel all" buttons that target nothing.
        if (items.isEmpty()) return
        if (items.size == SINGLE) {
            val item = items.first()
            if (item.state is DownloadState.Running) {
                builder.addAction(android.R.drawable.ic_media_pause, "Pause", action(DownloadService.ACTION_PAUSE, item.id))
            } else if (item.state is DownloadState.Paused) {
                builder.addAction(android.R.drawable.ic_media_play, "Resume", action(DownloadService.ACTION_RESUME, item.id))
            }
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", action(DownloadService.ACTION_CANCEL, item.id))
        } else {
            if (items.any { it.state is DownloadState.Running }) {
                builder.addAction(android.R.drawable.ic_media_pause, "Pause all", action(DownloadService.ACTION_PAUSE_ALL))
            } else {
                builder.addAction(android.R.drawable.ic_media_play, "Resume all", action(DownloadService.ACTION_RESUME_ALL))
            }
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel all", action(DownloadService.ACTION_CANCEL_ALL))
        }
    }

    private fun action(
        action: String,
        id: String? = null,
    ): PendingIntent {
        val intent =
            Intent(context, DownloadService::class.java).apply {
                this.action = action
                id?.let { putExtra(DownloadService.EXTRA_ID, it) }
            }
        // Distinct request code per (action,id) so actions don't collide on the same PendingIntent.
        val requestCode = (action + (id ?: "")).hashCode()
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun title(items: List<DownloadItem>): String {
        if (items.isEmpty()) return config.channelName
        if (items.size != SINGLE) {
            val count = items.size.coerceAtLeast(1)
            return "$count downloads"
        }
        return when (items.first().state) {
            is DownloadState.Paused -> "Download paused"
            is DownloadState.WaitingForNetwork -> "Waiting for network"
            else -> "Downloading"
        }
    }

    private fun text(
        items: List<DownloadItem>,
        withProgress: List<Pair<DownloadItem, DownloadProgress>>,
    ): String {
        if (items.size == SINGLE) {
            val progress = withProgress.firstOrNull()?.second ?: return ""
            return singleLine(items.first().state, progress)
        }
        val percent = aggregatePercent(withProgress.map { it.second })
        val speed = combinedSpeed(items)
        return listOfNotNull("$percent%", speed.takeIf { it.isNotEmpty() }).joinToString(" • ")
    }

    private fun singleLine(
        state: DownloadState,
        progress: DownloadProgress,
    ): String {
        val sizePart =
            if (progress.totalBytes > 0) {
                "${ByteFormatter.formatSize(progress.downloadedBytes)} / ${ByteFormatter.formatSize(progress.totalBytes)}"
            } else {
                ByteFormatter.formatSize(progress.downloadedBytes)
            }
        val percentPart = if (progress.totalBytes > 0) "${progress.percent}%" else null
        val speedPart =
            if (config.showSpeed && state is DownloadState.Running) {
                ByteFormatter.formatSpeed(progress.bytesPerSecond).takeIf { it.isNotEmpty() }
            } else {
                null
            }
        return listOfNotNull(sizePart, percentPart, speedPart).joinToString(" • ")
    }

    private fun combinedSpeed(items: List<DownloadItem>): String {
        if (!config.showSpeed) return ""
        val speed = items.mapNotNull { (it.state as? DownloadState.Running)?.progress?.bytesPerSecond }.sum()
        return if (speed > 0) ByteFormatter.formatSpeed(speed) else ""
    }

    private fun aggregatePercent(progresses: List<DownloadProgress>): Int {
        if (progresses.isEmpty()) return 0
        val average = progresses.map { it.fraction }.average()
        return (average * MAX_PROGRESS).toInt()
    }

    private fun DownloadItem.progress(): DownloadProgress? =
        when (val s = state) {
            is DownloadState.Running -> s.progress
            is DownloadState.Paused -> s.progress
            else -> null
        }

    private fun notificationManager(): NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private companion object {
        const val MAX_PROGRESS = 100
        const val SINGLE = 1
    }
}
