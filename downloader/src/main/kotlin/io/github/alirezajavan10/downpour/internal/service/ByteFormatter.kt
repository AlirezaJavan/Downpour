package io.github.alirezajavan10.downpour.internal.service

import java.util.Locale

internal object ByteFormatter {
    private const val UNIT = 1024.0
    private val SPEED_UNITS = listOf("B/s", "KB/s", "MB/s", "GB/s")
    private val SIZE_UNITS = listOf("B", "KB", "MB", "GB", "TB")

    fun formatSpeed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0) return ""
        var value = bytesPerSecond.toDouble()
        var unitIndex = 0
        while (value >= UNIT && unitIndex < SPEED_UNITS.lastIndex) {
            value /= UNIT
            unitIndex++
        }
        return String.format(Locale.US, "%.1f %s", value, SPEED_UNITS[unitIndex])
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= UNIT && unitIndex < SIZE_UNITS.lastIndex) {
            value /= UNIT
            unitIndex++
        }
        return String.format(Locale.US, "%.1f %s", value, SIZE_UNITS[unitIndex])
    }
}
