package io.github.alirezajavan.downpour.sample.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.alirezajavan.downpour.api.ConflictStrategy
import io.github.alirezajavan.downpour.sample.core.SampleDownpour
import io.github.alirezajavan.downpour.sample.core.SampleEvents
import io.github.alirezajavan.downpour.sample.core.SampleSettings
import io.github.alirezajavan.downpour.sample.core.SampleSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val store = SampleSettingsStore(application)

    private val _settings = MutableStateFlow(store.load())
    val settings: StateFlow<SampleSettings> = _settings

    fun update(transform: (SampleSettings) -> SampleSettings) {
        _settings.value = transform(_settings.value)
    }

    /**
     * Persists [settings] and rebuilds the shared [io.github.alirezajavan.downpour.api.DownloadManager]
     * from it right away -- no restart needed. Screens that already fetched the manager before this
     * call (e.g. one currently on screen) keep talking to the old, now shut-down engine until they
     * re-fetch via [SampleDownpour.getInstance], which happens naturally when a screen is recreated
     * (e.g. navigating back to it).
     */
    fun apply(context: Context) {
        store.save(_settings.value)
        SampleDownpour.applySettings(context, _settings.value)
    }

    fun exportQueue(context: Context) {
        viewModelScope.launch {
            try {
                val json = SampleDownpour.getInstance(context).exportQueue()
                val file = File(context.cacheDir, "queue_snapshot.json")
                file.writeText(json)
                SampleEvents.emit("Exported to ${file.absolutePath}")
            } catch (e: Exception) {
                SampleEvents.emit("Export failed: ${e.message}")
            }
        }
    }

    fun importQueue(context: Context) {
        viewModelScope.launch {
            try {
                val file = File(context.cacheDir, "queue_snapshot.json")
                if (!file.exists()) {
                    SampleEvents.emit("No snapshot found in cache")
                    return@launch
                }
                val json = file.readText()
                val ids = SampleDownpour.getInstance(context).importQueue(json, ConflictStrategy.FAIL)
                SampleEvents.emit("Imported ${ids.size} items")
            } catch (e: Exception) {
                SampleEvents.emit("Import failed: ${e.message}")
            }
        }
    }
}
