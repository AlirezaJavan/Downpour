package io.github.alirezajavan.downpour.internal.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.alirezajavan.downpour.internal.di.DownloaderGraph
import kotlinx.coroutines.flow.first

internal class DownloadRecoveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val graph = DownloaderGraph.getInstance(applicationContext)
        graph.engine.recover()
        awaitIdle(graph)
        return Result.success()
    }

    private suspend fun awaitIdle(graph: DownloaderGraph) {
        graph.repository.observeAllItems().first { items -> items.none { it.state.isActive } }
    }

    companion object {
        private const val UNIQUE_NAME = "downpour_recovery"

        fun schedule(
            context: Context,
            delayMillis: Long = 0,
        ) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request =
                OneTimeWorkRequestBuilder<DownloadRecoveryWorker>()
                    .setConstraints(constraints)
                    .setInitialDelay(delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build()

            val policy = if (delayMillis > 0) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP

            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, policy, request)
        }
    }
}
