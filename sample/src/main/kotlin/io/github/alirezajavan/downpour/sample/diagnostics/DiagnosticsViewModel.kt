package io.github.alirezajavan.downpour.sample.diagnostics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.alirezajavan.downpour.api.DiagnosticReport
import io.github.alirezajavan.downpour.api.DownloadItem
import io.github.alirezajavan.downpour.sample.core.SampleDownpour
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DiagnosticsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val manager = SampleDownpour.getInstance(application)

    val downloads: StateFlow<List<DownloadItem>> =
        manager
            .observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId

    private val _report = MutableStateFlow<DiagnosticReport?>(null)
    val report: StateFlow<DiagnosticReport?> = _report

    fun select(id: String) {
        _selectedId.value = id
        viewModelScope.launch { _report.value = manager.getDiagnosticReport(id) }
    }

    fun refresh() {
        _selectedId.value?.let { select(it) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
