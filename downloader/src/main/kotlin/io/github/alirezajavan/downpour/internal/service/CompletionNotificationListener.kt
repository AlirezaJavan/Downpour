package io.github.alirezajavan.downpour.internal.service

import android.app.NotificationManager
import io.github.alirezajavan.downpour.api.DownloadItem
import io.github.alirezajavan.downpour.api.DownloadListener
import io.github.alirezajavan.downpour.api.DownloadState

internal class CompletionNotificationListener(
    private val notificationManager: NotificationManager,
    private val factory: DownloadNotificationFactory,
) : DownloadListener {
    override fun onStateChanged(item: DownloadItem) {
        if (item.state !is DownloadState.Completed) return
        factory.ensureChannel()
        notificationManager.notify(item.id.hashCode(), factory.buildCompletion(item))
    }
}
