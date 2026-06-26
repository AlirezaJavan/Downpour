package io.github.alirezajavan10.downpour.internal.service

import android.content.Context
import io.github.alirezajavan10.downpour.internal.engine.DownloadServiceController
import io.github.alirezajavan10.downpour.internal.work.DownloadRecoveryWorker

internal class ForegroundServiceController(
    context: Context,
    private val notificationsEnabled: Boolean,
) : DownloadServiceController {
    private val appContext = context.applicationContext

    override fun onActiveCountChanged(activeCount: Int) {
        if (!notificationsEnabled) return
        if (activeCount > 0) {
            DownloadService.start(appContext)
            DownloadRecoveryWorker.schedule(appContext)
        } else {
            DownloadService.stop(appContext)
        }
    }
}
