package io.github.alirezajavan.downpour.internal.device

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public enum class ThermalState {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN,
    ;

    public val isThrottled: Boolean
        get() = this >= MODERATE

    public val isCriticalOrHigher: Boolean
        get() = this >= CRITICAL
}

internal interface ThermalMonitor {
    val thermalState: StateFlow<ThermalState>

    fun startMonitoring()

    fun stopMonitoring()
}

internal class DefaultThermalMonitor(
    private val context: Context,
) : ThermalMonitor {
    private val _thermalState = MutableStateFlow(ThermalState.NONE)
    override val thermalState: StateFlow<ThermalState> = _thermalState.asStateFlow()

    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null

    override fun startMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (thermalListener == null) {
                val listener =
                    PowerManager.OnThermalStatusChangedListener { status ->
                        _thermalState.value = mapThermalStatus(status)
                    }
                thermalListener = listener
                runCatching {
                    powerManager.addThermalStatusListener(listener)
                    _thermalState.value = mapThermalStatus(powerManager.currentThermalStatus)
                }
            }
        }
    }

    override fun stopMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalListener?.let { listener ->
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                runCatching { powerManager?.removeThermalStatusListener(listener) }
                thermalListener = null
            }
        }
    }

    private fun mapThermalStatus(status: Int): ThermalState =
        when (status) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalState.NONE
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL -> ThermalState.CRITICAL
            PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalState.EMERGENCY
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalState.SHUTDOWN
            else -> ThermalState.NONE
        }
}

internal class FakeThermalMonitor(
    initialState: ThermalState = ThermalState.NONE,
) : ThermalMonitor {
    private val _thermalState = MutableStateFlow(initialState)
    override val thermalState: StateFlow<ThermalState> = _thermalState.asStateFlow()

    fun setThermalState(state: ThermalState) {
        _thermalState.value = state
    }

    @Suppress("EmptyFunctionBlock")
    override fun startMonitoring() {}

    @Suppress("EmptyFunctionBlock")
    override fun stopMonitoring() {}
}
