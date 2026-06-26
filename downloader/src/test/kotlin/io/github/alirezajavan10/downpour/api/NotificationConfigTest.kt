package io.github.alirezajavan10.downpour.api

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NotificationConfigTest {
    @Test
    fun `default values are correct`() {
        val config = NotificationConfig()
        assertThat(config.channelId).isEqualTo(NotificationConfig.DEFAULT_CHANNEL_ID)
        assertThat(config.enabled).isTrue()

        @Test
        fun `custom values are stored correctly`() {
            val config = NotificationConfig(channelId = "custom", enabled = false)
            assertThat(config.channelId).isEqualTo("custom")
            assertThat(config.enabled).isFalse()
        }
    }

    @Test
    fun `custom values are stored correctly`() {
        val config = NotificationConfig(channelId = "custom", enabled = false)
        assertThat(config.channelId).isEqualTo("custom")
        assertThat(config.enabled).isFalse()
    }
}
