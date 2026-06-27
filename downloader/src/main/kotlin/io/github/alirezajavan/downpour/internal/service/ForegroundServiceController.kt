package io.github.alirezajavan.downpour.internal.service

import android.content.Context
import io.github.alirezajavan.downpour.internal.engine.DownloadServiceController
import io.github.alirezajavan.downpour.internal.work.DownloadRecoveryWorker

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
        }
        // When the running count hits 0 the service decides for itself whether to keep a persistent
        // paused notification (so it can be resumed from the shade) or remove it — based on whether
        // any downloads remain. Tearing it down here would kill the resume control.
    }
}
