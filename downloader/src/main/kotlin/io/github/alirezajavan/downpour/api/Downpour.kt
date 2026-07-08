package io.github.alirezajavan.downpour.api

import android.content.Context
import androidx.core.content.FileProvider
import io.github.alirezajavan.downpour.internal.di.DownloaderGraph
import java.io.File

public object Downpour {
    public fun getInstance(
        context: Context,
        config: DownloadManagerConfig = DownloadManagerConfig(),
    ): DownloadManager = DownloaderGraph.getInstance(context, config).downloadManager

    /**
     * Rebuilds the engine from [config], replacing whatever instance [getInstance] previously
     * handed out. Callers that already hold an older [DownloadManager] reference keep talking to
     * the now-shut-down engine -- re-fetch via [getInstance] after calling this to observe the new
     * one. In-flight downloads are not lost: they're requeued and resumed by the new engine.
     */
    public fun reconfigure(
        context: Context,
        config: DownloadManagerConfig,
    ): DownloadManager = DownloaderGraph.reconfigure(context, config).downloadManager

    public fun getFileUri(
        context: Context,
        item: DownloadItem,
    ): android.net.Uri {
        val destination = item.destination
        require(destination is DownloadDestination.File) { "Item must have a file destination" }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.downpour.fileprovider",
            File(destination.path),
        )
    }
}
