package io.github.alirezajavan.downpour.sample.downloads

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.alirezajavan.downpour.api.Checksum
import io.github.alirezajavan.downpour.api.DownloadDestination
import io.github.alirezajavan.downpour.api.DownloadItem
import io.github.alirezajavan.downpour.api.Downpour
import io.github.alirezajavan.downpour.api.downloadRequest
import io.github.alirezajavan.downpour.sample.core.SampleDownpour
import io.github.alirezajavan.downpour.sample.core.SampleEvents
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class DownloadsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val manager = SampleDownpour.getInstance(application)

    val downloads: StateFlow<List<DownloadItem>> =
        manager
            .observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun enqueue(form: NewDownloadForm) {
        val destination = File(getApplication<Application>().getExternalFilesDir(null), form.fileName())
        manager.enqueue(
            downloadRequest(url = form.url, destination = DownloadDestination.File(destination.absolutePath)) {
                priority(form.priority)
                networkType(form.networkType)
                conflictStrategy(form.conflictStrategy)
                maxConnections(form.maxConnections)
                tag(form.tag.ifBlank { "sample" })
                requiresCharging(form.requiresCharging)
                requiresBatteryNotLow(form.requiresBatteryNotLow)
                requiresStorageNotLow(form.requiresStorageNotLow)
                form.checksumHex.trim().takeIf { it.isNotEmpty() }?.let { hex ->
                    checksum(Checksum(form.checksumAlgorithm, hex))
                }
                form.mirrorUrl
                    .trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { mirror(it) }

                if (form.schedule.hasWindow) {
                    scheduleWindow(
                        form.schedule.scheduleStartMinuteOfDay!! / 60,
                        form.schedule.scheduleStartMinuteOfDay!! % 60,
                        form.schedule.scheduleEndMinuteOfDay!! / 60,
                        form.schedule.scheduleEndMinuteOfDay!! % 60,
                    )
                }

                form.schedule.scheduledAtMillis?.let { scheduleAt(it) }
            },
        )
        SampleEvents.emit("Queued • ${form.fileName()}")
    }

    fun openFile(item: DownloadItem) {
        val context = getApplication<Application>()
        runCatching {
            val uri = Downpour.getFileUri(context, item)
            val mimeType = context.contentResolver.getType(uri) ?: "*/*"
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
        }.onFailure {
            SampleEvents.emit("No app found to open this file")
        }
    }

    fun pause(id: String) = launch { manager.pause(id) }

    fun resume(id: String) = launch { manager.resume(id) }

    fun cancel(id: String) = launch { manager.cancel(id) }

    fun retry(id: String) = launch { manager.retry(id) }

    fun remove(id: String) = launch { manager.remove(id, deleteFile = true) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
