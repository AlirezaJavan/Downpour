package io.github.alirezajavan.downpour.internal.service

import android.content.Context
import io.github.alirezajavan.downpour.internal.engine.DownloadServiceController
import io.github.alirezajavan.downpour.internal.work.DownloadRecoveryWorker

internal class ForegroundServiceController(
    context: Context,
    private val notificationsEnabled: Boolean,
) : DownloadServiceController {
    private val appContext = context.applicationContext
    private var lastActiveCount = 0

    override fun onActiveCountChanged(activeCount: Int) {
        if (!notificationsEnabled) return

        // Only attempt to start the service on a transition from idle to active.
        // If it's already active, the service's own observer will handle notification updates.
        // This prevents ForegroundServiceStartNotAllowedException when schedule() is called
        // from the background (e.g. by adaptive tuning or network changes) after the initial
        // background-start exemption (like an Alarm) has expired.
        if (activeCount > 0 && lastActiveCount == 0) {
            DownloadService.start(appContext)
            DownloadRecoveryWorker.schedule(appContext)
        }

        lastActiveCount = activeCount
        // When the running count hits 0 the service decides for itself whether to keep a persistent
        // paused notification (so it can be resumed from the shade) or remove it — based on whether
        // any downloads remain. Tearing it down here would kill the resume control.
    }
}
