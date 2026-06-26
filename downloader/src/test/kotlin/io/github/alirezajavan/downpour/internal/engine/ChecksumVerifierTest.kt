package io.github.alirezajavan.downpour.internal.engine

import io.github.alirezajavan.downpour.api.Checksum
import io.github.alirezajavan.downpour.api.ChecksumAlgorithm
import io.github.alirezajavan.downpour.api.DownloadDestination
import io.github.alirezajavan.downpour.api.DownloadError
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream

class ChecksumVerifierTest {
    private val fileStore = mockk<FileStore>()

    @Test
    fun `verification passes for a matching sha256 digest`() {
        val content = "hello"
        val expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        val destination = DownloadDestination.File("path")

        every { fileStore.openReadable(destination) } returns ByteArrayInputStream(content.toByteArray())

        ChecksumVerifier.verify(fileStore, destination, Checksum(ChecksumAlgorithm.SHA256, expected))
    }

    @Test
    fun `verification throws for a mismatching digest`() {
        val content = "hello"
        val destination = DownloadDestination.File("path")

        every { fileStore.openReadable(destination) } returns ByteArrayInputStream(content.toByteArray())

        assertThrows<DownloadError.ContentValidation> {
            ChecksumVerifier.verify(fileStore, destination, Checksum(ChecksumAlgorithm.SHA256, "deadbeef"))
        }
    }

    @Test
    fun `verification passes for md5 digest`() {
        val content = "hello"
        val expected = "5d41402abc4b2a76b9719d911017c592"
        val destination = DownloadDestination.File("path")

        every { fileStore.openReadable(destination) } returns ByteArrayInputStream(content.toByteArray())

        ChecksumVerifier.verify(fileStore, destination, Checksum(ChecksumAlgorithm.MD5, expected))
    }
}
