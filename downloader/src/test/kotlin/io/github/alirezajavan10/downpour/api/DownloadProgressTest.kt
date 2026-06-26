package io.github.alirezajavan10.downpour.api

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DownloadProgressTest {
    @Test
    fun `fraction and percent reflect downloaded ratio`() {
        val progress = DownloadProgress(downloadedBytes = 250, totalBytes = 1000, bytesPerSecond = 0, etaMillis = 0)

        assertThat(progress.fraction).isWithin(TOLERANCE).of(0.25f)
        assertThat(progress.percent).isEqualTo(25)
    }

    @Test
    fun `unknown total size is reported as indeterminate`() {
        val progress = DownloadProgress.EMPTY

        assertThat(progress.isIndeterminate).isTrue()
        assertThat(progress.fraction).isEqualTo(0f)
    }

    @Test
    fun `fraction is clamped to one`() {
        val progress = DownloadProgress(downloadedBytes = 1500, totalBytes = 1000, bytesPerSecond = 0, etaMillis = 0)

        assertThat(progress.fraction).isEqualTo(1f)
    }

    @Test
    fun `percent is calculated correctly`() {
        val progress = DownloadProgress(downloadedBytes = 500, totalBytes = 1000, bytesPerSecond = 0, etaMillis = 0)
        assertThat(progress.percent).isEqualTo(50)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
