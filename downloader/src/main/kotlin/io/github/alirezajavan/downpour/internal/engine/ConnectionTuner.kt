package io.github.alirezajavan.downpour.internal.engine

internal class ConnectionTuner(
    minConnections: Int,
    maxConnections: Int,
) {
    // A caller-supplied minConnections above the per-request maxConnections cap must not win --
    // the request's own cap is the harder constraint.
    private val minConnections = minConnections.coerceAtMost(maxConnections)
    private val maxConnections = maxConnections
    private var lastConnections = -1
    private var lastSpeed = 0L

    fun decide(
        currentConnections: Int,
        currentSpeed: Long,
    ): Int = decideRaw(currentConnections, currentSpeed).coerceIn(minConnections, maxConnections)

    private fun decideRaw(
        currentConnections: Int,
        currentSpeed: Long,
    ): Int {
        if (lastConnections == -1) {
            lastConnections = currentConnections
            lastSpeed = currentSpeed
            // No history yet: probe upward to see whether adding a connection helps at all.
            return currentConnections + 1
        }

        return when {
            currentConnections > lastConnections -> {
                afterIncrease(currentConnections, currentSpeed)
            }

            currentConnections < lastConnections -> {
                afterDecrease(currentConnections, currentSpeed)
            }

            else -> {
                lastSpeed = currentSpeed
                currentConnections
            }
        }
    }

    // We just added a connection. A healthy gain scales roughly linearly with connection count
    // (e.g. 2 -> 3 connections should buy ~50% more throughput); require at least half of that
    // expected gain before trusting it enough to probe further.
    private fun afterIncrease(
        currentConnections: Int,
        currentSpeed: Long,
    ): Int {
        val speedGain = currentSpeed - lastSpeed
        val expectedLinearGain = lastSpeed.toDouble() / lastConnections
        val gainedEnough = speedGain > expectedLinearGain * MIN_GAIN_FRACTION && currentSpeed > lastSpeed

        if (gainedEnough) {
            lastConnections = currentConnections
            lastSpeed = currentSpeed
            return currentConnections + 1
        }
        // Plateaued or worse: hold here, unless the extra connection actively made things worse,
        // in which case back off to the last known-good count.
        return if (currentSpeed < lastSpeed * REGRESSION_THRESHOLD) lastConnections else currentConnections
    }

    // We just dropped a connection (e.g. after a 429 downgrade). If speed held up, settle at the
    // lower count; if it tanked, the drop wasn't worth it and we go back to the prior count.
    private fun afterDecrease(
        currentConnections: Int,
        currentSpeed: Long,
    ): Int {
        if (currentSpeed < lastSpeed * MIN_RETAINED_FRACTION) return lastConnections
        lastConnections = currentConnections
        lastSpeed = currentSpeed
        return currentConnections
    }

    private companion object {
        const val MIN_GAIN_FRACTION = 0.5
        const val REGRESSION_THRESHOLD = 0.8
        const val MIN_RETAINED_FRACTION = 0.9
    }
}
