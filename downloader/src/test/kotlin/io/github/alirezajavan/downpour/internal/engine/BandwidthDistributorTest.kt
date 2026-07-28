package io.github.alirezajavan.downpour.internal.engine

import com.google.common.truth.Truth.assertThat
import io.github.alirezajavan.downpour.api.Priority
import org.junit.jupiter.api.Test

class BandwidthDistributorTest {
    @Test
    fun `empty active items returns empty map`() {
        val result = BandwidthDistributor.calculateAllocations(emptyList(), globalMaxBytesPerSecond = 1_000_000L)
        assertThat(result).isEmpty()
    }

    @Test
    fun `unlimited global speed returns individual limits`() {
        val items =
            listOf(
                BandwidthItem("1", Priority.HIGH, individualMaxBytesPerSecond = 500_000L),
                BandwidthItem("2", Priority.LOW, individualMaxBytesPerSecond = 0L),
            )
        val result = BandwidthDistributor.calculateAllocations(items, globalMaxBytesPerSecond = 0L)

        assertThat(result["1"]).isEqualTo(500_000L)
        assertThat(result["2"]).isEqualTo(0L)
    }

    @Test
    fun `allocates proportional shares based on priority weights`() {
        // LOW = 1, NORMAL = 2, HIGH = 4. Total weight = 7
        val items =
            listOf(
                BandwidthItem("low", Priority.LOW),
                BandwidthItem("normal", Priority.NORMAL),
                BandwidthItem("high", Priority.HIGH),
            )
        val globalCap = 7_000_000L
        val result = BandwidthDistributor.calculateAllocations(items, globalMaxBytesPerSecond = globalCap)

        assertThat(result["low"]).isEqualTo(1_000_000L)
        assertThat(result["normal"]).isEqualTo(2_000_000L)
        assertThat(result["high"]).isEqualTo(4_000_000L)
    }

    @Test
    fun `respects lower individual caps when proportional share is higher`() {
        val items =
            listOf(
                BandwidthItem("1", Priority.HIGH, individualMaxBytesPerSecond = 1_000_000L),
                BandwidthItem("2", Priority.LOW, individualMaxBytesPerSecond = 0L),
            )
        val globalCap = 5_000_000L
        val result = BandwidthDistributor.calculateAllocations(items, globalMaxBytesPerSecond = globalCap)

        assertThat(result["1"]).isEqualTo(1_000_000L)
        assertThat(result["2"]).isEqualTo(1_000_000L)
    }
}
