package io.github.alirezajavan10.downpour.internal.util

import android.webkit.MimeTypeMap
import io.github.alirezajavan10.downpour.internal.network.RemoteFileInfo

internal object FilenameResolver {
    private const val DISPOSITION_FILENAME = "filename="
    private const val DISPOSITION_FILENAME_STAR = "filename*="

    fun resolve(
        url: String,
        info: RemoteFileInfo,
    ): String {
        val fromDisposition = info.contentDisposition?.let { parseDisposition(it) }
        if (!fromDisposition.isNullOrBlank()) return fromDisposition

        val fromUrl = url.substringAfterLast('/').substringBefore('?').substringBefore('#')
        if (fromUrl.isNotBlank() && fromUrl.contains('.')) return fromUrl

        val extension =
            info.contentType?.let {
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
