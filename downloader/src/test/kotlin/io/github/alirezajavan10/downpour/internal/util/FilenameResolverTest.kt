package io.github.alirezajavan10.downpour.internal.util

import com.google.common.truth.Truth.assertThat
import io.github.alirezajavan10.downpour.internal.network.RemoteFileInfo
import org.junit.jupiter.api.Test

class FilenameResolverTest {
    @Test
    fun `resolves from content disposition`() {
        val info =
            RemoteFileInfo(
                totalBytes = 100,
                acceptsRanges = true,
                etag = null,
                lastModified = null,
                contentType = "application/pdf",
                contentDisposition = "attachment; filename=\"document.pdf\"",
            )
        val name = FilenameResolver.resolve("https://example.com/file", info)
        assertThat(name).isEqualTo("document.pdf")
    }

    @Test
    fun `resolves from url when disposition is missing`() {
        val info =
            RemoteFileInfo(
                totalBytes = 100,
                acceptsRanges = true,
                etag = null,
                lastModified = null,
                contentType = "application/pdf",
                contentDisposition = null,
            )
        val name = FilenameResolver.resolve("https://example.com/manual.pdf", info)
        assertThat(name).isEqualTo("manual.pdf")
    }
}
