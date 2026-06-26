package io.github.alirezajavan.downpour.internal.engine

internal data class PartPlan(
    val partId: Long,
    val index: Int,
    val start: Long,
    val end: Long,
    val downloaded: Long,
) {
    val isOpenEnded: Boolean
        get() = end < 0

    val nextByte: Long
        get() = start + downloaded
}

internal data class DownloadPlan(
    val totalBytes: Long,
    val parts: List<PartPlan>,
    val ifRange: String?,
    val isMultiConnection: Boolean,
) {
    val alreadyDownloaded: Long
        get() = parts.sumOf { it.downloaded }

    val hasKnownSize: Boolean
        get() = totalBytes > 0
}
