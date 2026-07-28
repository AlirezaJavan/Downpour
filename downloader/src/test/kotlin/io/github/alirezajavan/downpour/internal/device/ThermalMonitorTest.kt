package io.github.alirezajavan.downpour.internal.device

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ThermalMonitorTest {
    @Test
    fun `thermal state thresholds assess throttling and critical levels correctly`() {
        assertThat(ThermalState.NONE.isThrottled).isFalse()
        assertThat(ThermalState.NONE.isCriticalOrHigher).isFalse()

        assertThat(ThermalState.LIGHT.isThrottled).isFalse()
        assertThat(ThermalState.LIGHT.isCriticalOrHigher).isFalse()

        assertThat(ThermalState.MODERATE.isThrottled).isTrue()
        assertThat(ThermalState.MODERATE.isCriticalOrHigher).isFalse()

        assertThat(ThermalState.SEVERE.isThrottled).isTrue()
        assertThat(ThermalState.SEVERE.isCriticalOrHigher).isFalse()

        assertThat(ThermalState.CRITICAL.isThrottled).isTrue()
        assertThat(ThermalState.CRITICAL.isCriticalOrHigher).isTrue()

        assertThat(ThermalState.EMERGENCY.isThrottled).isTrue()
        assertThat(ThermalState.EMERGENCY.isCriticalOrHigher).isTrue()

        assertThat(ThermalState.SHUTDOWN.isThrottled).isTrue()
        assertThat(ThermalState.SHUTDOWN.isCriticalOrHigher).isTrue()
    }

    @Test
    fun `fake thermal monitor emits state updates`() {
        val monitor = FakeThermalMonitor(ThermalState.NONE)
        assertThat(monitor.thermalState.value).isEqualTo(ThermalState.NONE)

        monitor.setThermalState(ThermalState.MODERATE)
        assertThat(monitor.thermalState.value).isEqualTo(ThermalState.MODERATE)
        assertThat(monitor.thermalState.value.isThrottled).isTrue()

        monitor.setThermalState(ThermalState.CRITICAL)
        assertThat(monitor.thermalState.value.isCriticalOrHigher).isTrue()
    }
}
