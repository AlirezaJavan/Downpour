package io.github.alirezajavan.downpour.api

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DownloadManagerConfigTest {
    @Test
    fun `default values are correct`() {
        val config = DownloadManagerConfig()
        assertThat(config.maxConcurrentDownloads).isEqualTo(DownloadManagerConfig.DEFAULT_MAX_CONCURRENT)
    }

    @Test
    fun `throws on invalid concurrent downloads`() {
        assertThrows<IllegalArgumentException> {
            DownloadManagerConfig(maxConcurrentDownloads = 0)
        }
    }
}
