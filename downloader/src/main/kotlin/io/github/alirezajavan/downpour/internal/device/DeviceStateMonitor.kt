package io.github.alirezajavan.downpour.internal.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal data class DeviceState(
    val isCharging: Boolean,
    val isBatteryLow: Boolean,
    val isStorageLow: Boolean,
) {
    fun satisfies(
        requiresCharging: Boolean,
        requiresBatteryNotLow: Boolean,
        requiresStorageNotLow: Boolean,
    ): Boolean =
        (!requiresCharging || isCharging) &&
            (!requiresBatteryNotLow || !isBatteryLow) &&
            (!requiresStorageNotLow || !isStorageLow)
}

internal class DeviceStateMonitor(
    private val context: Context,
) {
    fun snapshot(): DeviceState {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return DeviceState(
            isCharging = battery.isCharging(),
            isBatteryLow = battery.batteryFraction() <= BATTERY_LOW_FRACTION,
            isStorageLow = checkStorageLow(),
        )
    }

    val changes: Flow<DeviceState> =
        callbackFlow {
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        trySend(snapshot())
                    }
                }
            ContextCompat.registerReceiver(
                context,
                receiver,
                deviceStateFilter(),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            trySend(snapshot())
            awaitClose { context.unregisterReceiver(receiver) }
        }.distinctUntilChanged()

    private fun checkStorageLow(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val storageManager = context.getSystemService(StorageManager::class.java)
            try {
                val uuid = storageManager.getUuidForPath(context.filesDir)
                storageManager.getAllocatableBytes(uuid) < STORAGE_LOW_BYTES
            } catch (_: Exception) {
                context.filesDir.usableSpace < STORAGE_LOW_BYTES
            }
        } else {
            context.filesDir.usableSpace < STORAGE_LOW_BYTES
        }

    private fun deviceStateFilter(): IntentFilter =
        IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            @Suppress("DEPRECATION")
            addAction(Intent.ACTION_DEVICE_STORAGE_LOW)
            @Suppress("DEPRECATION")
            addAction(Intent.ACTION_DEVICE_STORAGE_OK)
        }

    private fun Intent?.isCharging(): Boolean {
        val status = this?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun Intent?.batteryFraction(): Float {
        val level = this?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = this?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) return 1f
        return level.toFloat() / scale
    }

    private companion object {
        const val BATTERY_LOW_FRACTION = 0.15f
        const val STORAGE_LOW_BYTES = 100L * 1024 * 1024
    }
}
