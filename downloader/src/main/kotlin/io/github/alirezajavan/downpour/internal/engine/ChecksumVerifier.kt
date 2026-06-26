package io.github.alirezajavan.downpour.internal.engine

import io.github.alirezajavan.downpour.api.Checksum
import io.github.alirezajavan.downpour.api.ChecksumAlgorithm
import io.github.alirezajavan.downpour.api.DownloadDestination
import io.github.alirezajavan.downpour.api.DownloadError
import java.security.MessageDigest

internal object ChecksumVerifier {
    private const val BUFFER_SIZE = 64 * 1024
    private const val END_OF_STREAM = -1
    private const val HEX_RADIX = 0xFF

    fun verify(
        fileStore: FileStore,
        destination: DownloadDestination,
        checksum: Checksum,
    ) {
        val actual = digest(fileStore, destination, checksum.algorithm)
        if (!checksum.matches(actual)) {
            throw DownloadError.ContentValidation(
                "Checksum mismatch: expected ${checksum.expectedHex}, computed $actual",
            )
        }
    }

    private fun digest(
        fileStore: FileStore,
        destination: DownloadDestination,
        algorithm: ChecksumAlgorithm,
    ): String {
        val messageDigest = MessageDigest.getInstance(algorithm.javaName)
        fileStore.openReadable(destination).use { stream ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read == END_OF_STREAM) break
                messageDigest.update(buffer, 0, read)
            }
        }
        return messageDigest.digest().toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte ->
            (byte.toInt() and HEX_RADIX).toString(HEX_BASE).padStart(HEX_WIDTH, '0')
        }

    private const val HEX_BASE = 16
    private const val HEX_WIDTH = 2
}
