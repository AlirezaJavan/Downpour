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
    ): Int {
        val result = decideRaw(currentConnections, currentSpeed)
        return result.coerceIn(minConnections, maxConnections)
    }

    private fun decideRaw(
        currentConnections: Int,
        currentSpeed: Long,
    ): Int {
        if (lastConnections == -1) {
            lastConnections = currentConnections
            lastSpeed = currentSpeed
            // Try to increase initially to see if it helps
            return currentConnections + 1
        }

        if (currentConnections > lastConnections) {
            // We increased connections. Did speed improve significantly?
            // "Roughly linearly" means if we went from 2 to 3 (50% increase in connections),
            // we expect something like 50% increase in speed.
            // Let's be conservative and require at least 50% of the expected linear gain.

            val speedGain = currentSpeed - lastSpeed
            val expectedLinearGain = lastSpeed.toDouble() / lastConnections

            val threshold = expectedLinearGain * 0.5

            if (speedGain > threshold && currentSpeed > lastSpeed) {
                // It helped, try more
                lastConnections = currentConnections
                lastSpeed = currentSpeed
                return currentConnections + 1
            } else {
                // Plateaued or worse. Hold or go back?
                // Plan says "hold or reduce". Let's hold for now, or maybe reduce if it's much worse.
                if (currentSpeed < lastSpeed * 0.8) { // 20% drop
                    return lastConnections // Go back
                }
                return currentConnections // Hold
            }
        } else if (currentConnections < lastConnections) {
            // We reduced. Did speed stay the same?
            if (currentSpeed >= lastSpeed * 0.9) {
                // Reducing didn't hurt much, maybe stay here or reduce more?
                lastConnections = currentConnections
                lastSpeed = currentSpeed
                return currentConnections
            } else {
                // Reducing hurt, go back up?
                return lastConnections
            }
        }

        // currentConnections == lastConnections
        // Periodically try to increase again?
        // Maybe if speed is zero or very low, we might be stalled.

        lastSpeed = currentSpeed
        return currentConnections
    }
}
