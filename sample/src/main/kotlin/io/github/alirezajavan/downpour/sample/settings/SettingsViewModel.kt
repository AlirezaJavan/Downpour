package io.github.alirezajavan.downpour.sample.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import io.github.alirezajavan.downpour.sample.core.SampleSettings
import io.github.alirezajavan.downpour.sample.core.SampleSettingsStore
import io.github.alirezajavan.downpour.sample.core.restartApp
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

    fun applyAndRestart(context: Context) {
        store.save(_settings.value)
        restartApp(context)
    }
}
