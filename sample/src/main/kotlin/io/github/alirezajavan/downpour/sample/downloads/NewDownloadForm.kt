package io.github.alirezajavan.downpour.sample.downloads

import io.github.alirezajavan.downpour.api.ChecksumAlgorithm
import io.github.alirezajavan.downpour.api.ConflictStrategy
import io.github.alirezajavan.downpour.api.DownloadSchedule
import io.github.alirezajavan.downpour.api.NetworkType
import io.github.alirezajavan.downpour.api.Priority
import io.github.alirezajavan.downpour.sample.core.SampleCatalog

/** Everything the `DownloadRequest` DSL exposes, mirrored as UI-editable state (Phase 1/2 constraints). */
data class NewDownloadForm(
    val url: String = SampleCatalog.DEFAULT_URL,
    val priority: Priority = Priority.NORMAL,
    val networkType: NetworkType = NetworkType.ANY,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.RENAME,
    val maxConnections: Int = 4,
    val tag: String = "sample",
    val requiresCharging: Boolean = false,
    val requiresBatteryNotLow: Boolean = false,
    val requiresStorageNotLow: Boolean = false,
    val checksumAlgorithm: ChecksumAlgorithm = ChecksumAlgorithm.SHA256,
    val checksumHex: String = "",
    val mirrorUrl: String = "",
    val schedule: DownloadSchedule = DownloadSchedule(),
) {
    fun fileName(): String = url.substringAfterLast('/').ifBlank { "download_${System.currentTimeMillis()}" }
}
