package io.github.alirezajavan.downpour.sample.core

import io.github.alirezajavan.downpour.api.DownloadDestination
import io.github.alirezajavan.downpour.api.DownloadItem

/** [DownloadItemCard] resolves this internally; the sample app is a separate module and needs its own copy. */
fun DownloadItem.displayName(): String =
    when (val dest = destination) {
        is DownloadDestination.File -> dest.path.substringAfterLast('/')
        is DownloadDestination.Uri -> dest.uriString.substringAfterLast('/')
    }

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "unknown"
    if (bytes < UNIT) return "$bytes B"
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= UNIT && unitIndex < UNITS.lastIndex) {
        value /= UNIT
        unitIndex++
    }
    return "%.1f %s".format(value, UNITS[unitIndex])
}

private const val UNIT = 1024.0
private val UNITS = arrayOf("KB", "MB", "GB", "TB")
