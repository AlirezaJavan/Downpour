package io.github.alirezajavan.downpour.internal.util

import android.webkit.MimeTypeMap
import io.github.alirezajavan.downpour.api.FilenameResolver
import io.github.alirezajavan.downpour.api.RemoteFileMetadata

internal object DefaultFilenameResolver : FilenameResolver {
    private const val DISPOSITION_FILENAME = "filename="
    private const val DISPOSITION_FILENAME_STAR = "filename*="

    override fun resolve(metadata: RemoteFileMetadata): String {
        val fromDisposition = metadata.contentDisposition?.let { parseDisposition(it) }
        if (!fromDisposition.isNullOrBlank()) return fromDisposition

        val fromUrl =
            metadata.url
                .substringAfterLast('/')
                .substringBefore('?')
                .substringBefore('#')
        if (fromUrl.isNotBlank() && fromUrl.contains('.')) return fromUrl

        val extension =
            metadata.contentType?.let {
                MimeTypeMap.getSingleton().getExtensionFromMimeType(it.substringBefore(';'))
            }

        val baseName = if (fromUrl.isNotBlank()) fromUrl else "download_${System.currentTimeMillis()}"
        return if (!extension.isNullOrBlank()) "$baseName.$extension" else baseName
    }

    private fun parseDisposition(disposition: String): String? {
        // Simple parsing, could be improved for RFC 5987 (filename*)
        return if (disposition.contains(DISPOSITION_FILENAME_STAR)) {
            disposition.substringAfter(DISPOSITION_FILENAME_STAR).substringAfter("''").trim('"')
        } else if (disposition.contains(DISPOSITION_FILENAME)) {
            disposition
                .substringAfter(DISPOSITION_FILENAME)
                .substringBefore(';')
                .trim()
                .trim('"')
        } else {
            null
        }
    }
}
