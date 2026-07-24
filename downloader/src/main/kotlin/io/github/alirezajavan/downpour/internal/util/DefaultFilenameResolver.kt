package io.github.alirezajavan.downpour.internal.util

import android.webkit.MimeTypeMap
import io.github.alirezajavan.downpour.api.FilenameResolver
import io.github.alirezajavan.downpour.api.RemoteFileMetadata

internal object DefaultFilenameResolver : FilenameResolver {
    private val FILENAME_REGEX = """(?i)filename\s*=\s*(?:"([^"]*)"|([^;\s]*)?)""".toRegex()
    private val FILENAME_STAR_REGEX = """(?i)filename\*\s*=\s*([^\s']*)'([^\s']*)'\s*([^\s;]*)""".toRegex()

    override fun resolve(metadata: RemoteFileMetadata): String {
        val fromDisposition = metadata.contentDisposition?.let { parseDisposition(it) }
        if (!fromDisposition.isNullOrBlank()) return fromDisposition

        val fromUrl =
            metadata.url
                .substringAfterLast('/')
                .substringBefore('?')
                .substringBefore('#')
        if (fromUrl.isNotBlank() && fromUrl.contains('.')) return sanitizeFilename(fromUrl)

        val extension =
            metadata.contentType?.let {
                MimeTypeMap.getSingleton().getExtensionFromMimeType(it.substringBefore(';'))
            }

        val baseName = if (fromUrl.isNotBlank()) fromUrl else "download_${System.currentTimeMillis()}"
        val sanitizedBase = sanitizeFilename(baseName)
        return if (!extension.isNullOrBlank()) "$sanitizedBase.$extension" else sanitizedBase
    }

    private fun parseDisposition(disposition: String): String? {
        // Try filename* (RFC 5987) first as it has higher precedence
        val starMatch = FILENAME_STAR_REGEX.find(disposition)
        if (starMatch != null) {
            val charsetName = starMatch.groupValues[1].ifBlank { "UTF-8" }
            val encodedValue = starMatch.groupValues[3]
            runCatching {
                return sanitizeFilename(java.net.URLDecoder.decode(encodedValue, charsetName))
            }
        }

        val standardMatch = FILENAME_REGEX.find(disposition)
        if (standardMatch != null) {
            val quoted = standardMatch.groupValues[1]
            val unquoted = standardMatch.groupValues[2]
            val value = if (quoted.isNotEmpty()) quoted else unquoted
            if (value.isNotBlank()) {
                return sanitizeFilename(value)
            }
        }
        return null
    }

    private fun sanitizeFilename(filename: String): String = filename.replace('/', '_').replace('\\', '_').trim()
}
