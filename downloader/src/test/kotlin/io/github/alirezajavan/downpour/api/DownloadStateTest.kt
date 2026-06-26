package io.github.alirezajavan.downpour.api

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DownloadStateTest {
    @Test
    fun `isTerminal returns correct values`() {
        assertThat(DownloadState.Queued.isTerminal).isFalse()
        assertThat(DownloadState.Running(DownloadProgress.EMPTY).isTerminal).isFalse()
        assertThat(DownloadState.Completed(DownloadDestination.File("path"), 100).isTerminal).isTrue()
        assertThat(DownloadState.Cancelled.isTerminal).isTrue()
    }

    @Test
    fun `isActive returns correct values`() {
        assertThat(DownloadState.Queued.isActive).isTrue()
        assertThat(DownloadState.Running(DownloadProgress.EMPTY).isActive).isTrue()
        assertThat(DownloadState.Completed(DownloadDestination.File("path"), 100).isActive).isFalse()
    }
}
