package io.github.alirezajavan.downpour.sample

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.alirezajavan.downpour.api.ConflictStrategy
import io.github.alirezajavan.downpour.api.DownloadDestination
import io.github.alirezajavan.downpour.api.DownloadItem
import io.github.alirezajavan.downpour.api.DownloadManagerConfig
import io.github.alirezajavan.downpour.api.Downpour
import io.github.alirezajavan.downpour.api.NetworkType
import io.github.alirezajavan.downpour.api.Priority
import io.github.alirezajavan.downpour.api.downloadRequest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class DownloadsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val config =
        DownloadManagerConfig(
            verbose = true,
        )
    private val manager = Downpour.getInstance(application, config)

    val downloads: StateFlow<List<DownloadItem>> =
        manager
            .observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun enqueueSample(
        url: String,
        priority: Priority = Priority.NORMAL,
        networkType: NetworkType = NetworkType.ANY,
        conflictStrategy: ConflictStrategy = ConflictStrategy.RENAME,
    ) {
        val destination = File(getApplication<Application>().getExternalFilesDir(null), fileName(url))
        manager.enqueue(
            downloadRequest(url = url, destination = DownloadDestination.File(destination.absolutePath)) {
                priority(priority)
                networkType(networkType)
                conflictStrategy(conflictStrategy)
                maxConnections(MAX_CONNECTIONS)
                tag("sample-tag")
            },
        )
    }

    fun openFile(item: DownloadItem) {
        // Use Downpour.getFileUri and start Activity
    }

    fun showReport(id: String) {
        viewModelScope.launch {
            val report = manager.getDiagnosticReport(id)
            // Show report UI or log
        }
    }

    fun pause(id: String) = launch { manager.pause(id) }

    fun resume(id: String) = launch { manager.resume(id) }

    fun cancel(id: String) = launch { manager.cancel(id) }

    fun remove(id: String) = launch { manager.remove(id, deleteFile = true) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun fileName(url: String): String = url.substringAfterLast('/').ifBlank { "download_${System.currentTimeMillis()}" }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MAX_CONNECTIONS = 2
    }
}
