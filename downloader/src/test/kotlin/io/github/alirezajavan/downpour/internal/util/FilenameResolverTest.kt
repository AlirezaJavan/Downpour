package io.github.alirezajavan.downpour.internal.util

import com.google.common.truth.Truth.assertThat
import io.github.alirezajavan.downpour.api.RemoteFileMetadata
import org.junit.jupiter.api.Test

class FilenameResolverTest {
    @Test
    fun `resolves from content disposition`() {
        val metadata =
            RemoteFileMetadata(
                url = "https://example.com/file",
                contentDisposition = "attachment; filename=\"document.pdf\"",
                contentType = "application/pdf",
            )
        val name = DefaultFilenameResolver.resolve(metadata)
        assertThat(name).isEqualTo("document.pdf")
    }

    @Test
    fun `resolves from url when disposition is missing`() {
        val metadata =
            RemoteFileMetadata(
                url = "https://example.com/manual.pdf",
                contentDisposition = null,
                contentType = "application/pdf",
            )
        val name = DefaultFilenameResolver.resolve(metadata)
        assertThat(name).isEqualTo("manual.pdf")
    }
}
