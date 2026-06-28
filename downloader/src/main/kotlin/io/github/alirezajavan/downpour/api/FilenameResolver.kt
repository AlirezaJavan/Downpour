package io.github.alirezajavan.downpour.api

import io.github.alirezajavan.downpour.internal.util.DefaultFilenameResolver

public fun interface FilenameResolver {
    public fun resolve(metadata: RemoteFileMetadata): String

    public companion object {
        public val Default: FilenameResolver = DefaultFilenameResolver
    }
}

public data class RemoteFileMetadata(
    val url: String,
    val contentDisposition: String?,
    val contentType: String?,
)
