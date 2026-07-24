package io.github.alirezajavan.downpour.api

import androidx.compose.runtime.Immutable

@Immutable
public data class DownloadItem(
    val id: String,
    val url: String,
    val destination: DownloadDestination,
    val state: DownloadState,
    val priority: Priority,
    val conflictStrategy: ConflictStrategy,
    val tag: String?,
    val metadata: Map<String, String>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    @Deprecated("Use destination instead")
    val filePath: String
        get() = (destination as? DownloadDestination.File)?.path ?: ""
}
