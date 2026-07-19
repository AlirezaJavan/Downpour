package io.github.alirezajavan.downpour.internal.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.alirezajavan.downpour.internal.di.DownloaderGraph
import kotlin.time.Duration.Companion.seconds

internal class DownloadRecoveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val graph = DownloaderGraph.getInstance(applicationContext)
        graph.engine.recover()
        // Wait a bit to ensure the engine had time to start foreground services if needed.
        // Once the service is up, the process is safe even if this worker finishes.
        kotlinx.coroutines.delay(5.seconds)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "downpour_recovery"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<DownloadRecoveryWorker>().build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
