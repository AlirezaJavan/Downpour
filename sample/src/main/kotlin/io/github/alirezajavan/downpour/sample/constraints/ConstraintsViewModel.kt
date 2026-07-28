package io.github.alirezajavan.downpour.sample.constraints

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.alirezajavan.downpour.api.Checksum
import io.github.alirezajavan.downpour.api.DownloadDestination
import io.github.alirezajavan.downpour.api.downloadRequest
import io.github.alirezajavan.downpour.sample.core.SampleDownpour
import io.github.alirezajavan.downpour.sample.core.SampleEvents
import io.github.alirezajavan.downpour.sample.downloads.NewDownloadForm
import java.io.File

class ConstraintsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val manager = SampleDownpour.getInstance(application)

    fun enqueue(form: NewDownloadForm) {
        val fileName = form.fileName()
        val destinationFile = File(getApplication<Application>().getExternalFilesDir(null), fileName)
        manager.enqueue(
            downloadRequest(url = form.url, destination = DownloadDestination.File(destinationFile.absolutePath)) {
                priority(form.priority)
                networkType(form.networkType)
                conflictStrategy(form.conflictStrategy)
                duplicatePolicy(form.duplicatePolicy)
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

                form.schedule.startTimeMillis?.let { start ->
                    schedule(start, form.schedule.endTimeMillis)
                }
            },
        )
        SampleEvents.emit("Queued • $fileName")
    }
}
