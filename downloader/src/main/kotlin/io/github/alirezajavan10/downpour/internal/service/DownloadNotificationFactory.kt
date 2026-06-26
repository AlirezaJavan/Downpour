package io.github.alirezajavan10.downpour.internal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.alirezajavan10.downpour.api.DownloadItem
import io.github.alirezajavan10.downpour.api.DownloadState
import io.github.alirezajavan10.downpour.api.NotificationConfig

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
        val running = activeItems.mapNotNull { it.state as? DownloadState.Running }
        val builder =
            NotificationCompat
                .Builder(context, config.channelId)
                .setSmallIcon(config.smallIconRes)
                .setContentTitle(title(activeItems.size))
                .setContentText(text(running))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(MAX_PROGRESS, aggregatePercent(running), running.isEmpty())

        addActions(builder, activeItems)

        return builder.build()
    }

    private fun addActions(
        builder: NotificationCompat.Builder,
        items: List<DownloadItem>,
    ) {
        if (items.size == SINGLE) {
            val item = items.first()
            val id = item.id
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                actionIntent(DownloadService.ACTION_PAUSE, id),
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                actionIntent(DownloadService.ACTION_CANCEL, id),
            )
        } else {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause All",
                actionIntent(DownloadService.ACTION_PAUSE_ALL),
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel All",
                actionIntent(DownloadService.ACTION_CANCEL_ALL),
            )
        }
    }

    private fun actionIntent(
        action: String,
        id: String? = null,
    ): PendingIntent {
        val intent =
            Intent(context, DownloadService::class.java).apply {
                this.action = action
                id?.let { putExtra(DownloadService.EXTRA_ID, it) }
            }
        return PendingIntent.getService(
            context,
            id?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun title(count: Int): String = if (count == SINGLE) SINGLE_TITLE else "$count downloads in progress"

    private fun text(running: List<DownloadState.Running>): String {
        if (!config.showSpeed || running.isEmpty()) return ""
        val speed = running.sumOf { it.progress.bytesPerSecond }
        return ByteFormatter.formatSpeed(speed)
    }

    private fun aggregatePercent(running: List<DownloadState.Running>): Int {
        if (running.isEmpty()) return 0
        val average = running.map { it.progress.fraction }.average()
        return (average * MAX_PROGRESS).toInt()
    }

    private fun notificationManager(): NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private companion object {
        const val MAX_PROGRESS = 100
        const val SINGLE = 1
        const val SINGLE_TITLE = "Downloading"
    }
}
