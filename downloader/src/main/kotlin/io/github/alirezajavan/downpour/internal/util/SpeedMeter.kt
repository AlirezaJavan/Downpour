package io.github.alirezajavan.downpour.internal.util

import io.github.alirezajavan.downpour.api.DownloadProgress

internal class SpeedMeter(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val timestamps = ArrayDeque<Long>()
    private val byteCounts = ArrayDeque<Long>()

    fun sample(
        totalDownloaded: Long,
        totalBytes: Long,
    ): Reading {
        val now = clock()
        timestamps.addLast(now)
        byteCounts.addLast(totalDownloaded)
        evictOlderThan(now - windowMillis)

        val speed = computeSpeed()
        val eta = computeEta(speed, totalDownloaded, totalBytes)
        return Reading(bytesPerSecond = speed, etaMillis = eta)
    }

    private fun evictOlderThan(threshold: Long) {
        while (timestamps.size > MIN_SAMPLES && timestamps.first() < threshold) {
            timestamps.removeFirst()
            byteCounts.removeFirst()
        }
    }

    private fun computeSpeed(): Long {
        if (timestamps.size < MIN_SAMPLES) return 0
        val elapsedMillis = timestamps.last() - timestamps.first()
        if (elapsedMillis <= 0) return 0
        val movedBytes = byteCounts.last() - byteCounts.first()
        return movedBytes * MILLIS_PER_SECOND / elapsedMillis
    }

    private fun computeEta(
        speed: Long,
        downloaded: Long,
        total: Long,
    ): Long {
        if (speed <= 0 || total <= DownloadProgress.UNKNOWN) return DownloadProgress.UNKNOWN
        val remaining = (total - downloaded).coerceAtLeast(0)
        return remaining * MILLIS_PER_SECOND / speed
    }

    data class Reading(
        val bytesPerSecond: Long,
        val etaMillis: Long,
    )

    private companion object {
        const val DEFAULT_WINDOW_MILLIS = 5_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val MIN_SAMPLES = 2
    }
}
