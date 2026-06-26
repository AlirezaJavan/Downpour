package io.github.alirezajavan.downpour.internal.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SpeedMeterTest {
    private var now = 0L
    private val meter = SpeedMeter(clock = { now })

    @Test
    fun `first sample reports zero speed`() {
        val reading = meter.sample(totalDownloaded = 0, totalBytes = 1000)

        assertThat(reading.bytesPerSecond).isEqualTo(0)
    }

    @Test
    fun `speed is derived from bytes over elapsed window`() {
        meter.sample(totalDownloaded = 0, totalBytes = 2000)
        now = 1000

        val reading = meter.sample(totalDownloaded = 1000, totalBytes = 2000)

        assertThat(reading.bytesPerSecond).isEqualTo(1000)
        assertThat(reading.etaMillis).isEqualTo(1000)
    }

    @Test
    fun `eta is unknown when total size is unknown`() {
        meter.sample(totalDownloaded = 0, totalBytes = -1)
        now = 1000

        val reading = meter.sample(totalDownloaded = 1000, totalBytes = -1)

        assertThat(reading.etaMillis).isEqualTo(-1)
    }
}
