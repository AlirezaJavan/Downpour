package io.github.alirezajavan.downpour.internal.engine

import io.github.alirezajavan.downpour.api.Priority

internal data class BandwidthItem(
    val id: String,
    val priority: Priority,
    val individualMaxBytesPerSecond: Long = 0L,
)

/**
 * Calculates priority-weighted bandwidth shares across active downloads given a global
 * throughput limit (`maxBytesPerSecond`).
 */
internal object BandwidthDistributor {
    /**
     * Weight mapping for priority levels:
     * - LOW = 1
     * - NORMAL = 2
     * - HIGH = 4
     */
    fun getWeight(priority: Priority): Int =
        when (priority) {
            Priority.LOW -> 1
            Priority.NORMAL -> 2
            Priority.HIGH -> 4
        }

    /**
     * Computes the allocated bytes per second for each active download item.
     *
     * @param activeItems List of active download items with their priority and individual limits.
     * @param globalMaxBytesPerSecond Global throughput limit in bytes/sec (0 = unlimited).
     * @return Map of download ID to allocated bytes/sec limit (0 = unlimited).
     */
    @Suppress("ReturnCount")
    fun calculateAllocations(
        activeItems: List<BandwidthItem>,
        globalMaxBytesPerSecond: Long,
    ): Map<String, Long> {
        if (activeItems.isEmpty()) return emptyMap()

        if (globalMaxBytesPerSecond <= 0) {
            return activeItems.associate { it.id to maxOf(0L, it.individualMaxBytesPerSecond) }
        }

        val totalWeight = activeItems.sumOf { getWeight(it.priority) }
        if (totalWeight <= 0) {
            return activeItems.associate { it.id to 0L }
        }

        val allocations = mutableMapOf<String, Long>()
        for (item in activeItems) {
            val weight = getWeight(item.priority)
            val proportionalShare = (globalMaxBytesPerSecond.toDouble() * weight / totalWeight).toLong()

            val effectiveLimit =
                if (item.individualMaxBytesPerSecond > 0) {
                    minOf(proportionalShare, item.individualMaxBytesPerSecond)
                } else {
                    proportionalShare
                }

            allocations[item.id] = maxOf(1L, effectiveLimit)
        }

        return allocations
    }
}
