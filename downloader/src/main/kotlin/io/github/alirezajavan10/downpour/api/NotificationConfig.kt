package io.github.alirezajavan10.downpour.api

import androidx.annotation.DrawableRes

public data class NotificationConfig(
    val channelId: String = DEFAULT_CHANNEL_ID,
    val channelName: String = DEFAULT_CHANNEL_NAME,
    @param:DrawableRes val smallIconRes: Int = android.R.drawable.stat_sys_download,
    val showSpeed: Boolean = true,
    val enabled: Boolean = true,
) {
    public companion object {
        public const val DEFAULT_CHANNEL_ID: String = "downpour_downloads"
        public const val DEFAULT_CHANNEL_NAME: String = "Downloads"
    }
}
