package io.github.alirezajavan.downpour.sample.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import io.github.alirezajavan.downpour.sample.core.SampleDownpour
import io.github.alirezajavan.downpour.sample.core.SampleSettings
import io.github.alirezajavan.downpour.sample.core.SampleSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
}
