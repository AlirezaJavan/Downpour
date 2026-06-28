package io.github.alirezajavan.downpour.api

import android.app.PendingIntent
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat

public data class NotificationConfig(
    val channelId: String = DEFAULT_CHANNEL_ID,
    val channelName: String = DEFAULT_CHANNEL_NAME,
    @param:DrawableRes val smallIconRes: Int = android.R.drawable.stat_sys_download,
    val showSpeed: Boolean = true,
    val enabled: Boolean = true,
    val contentIntentProvider: ContentIntentProvider? = null,
    val showCompletionNotification: Boolean = false,
    val customizer: NotificationCustomizer? = null,
) {
    public companion object {
        public const val DEFAULT_CHANNEL_ID: String = "downpour_downloads"
        public const val DEFAULT_CHANNEL_NAME: String = "Downloads"
    }
}

public fun interface ContentIntentProvider {
    public fun provide(activeItems: List<DownloadItem>): PendingIntent?
}

public fun interface NotificationCustomizer {
    public fun customize(
        builder: NotificationCompat.Builder,
        activeItems: List<DownloadItem>,
    )
}
