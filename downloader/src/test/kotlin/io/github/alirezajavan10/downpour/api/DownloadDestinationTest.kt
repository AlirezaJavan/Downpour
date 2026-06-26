package io.github.alirezajavan10.downpour.api

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DownloadDestinationTest {
    @Test
    fun `file destination stores path`() {
        val dest = DownloadDestination.File("path")
        assertThat(dest.path).isEqualTo("path")
    }

    @Test
    fun `uri destination stores uri`() {
        val dest = DownloadDestination.Uri("uri")
        assertThat(dest.uriString).isEqualTo("uri")
    }
}
