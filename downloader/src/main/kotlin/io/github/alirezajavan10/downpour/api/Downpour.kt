package io.github.alirezajavan10.downpour.api

import android.content.Context
import androidx.core.content.FileProvider
import io.github.alirezajavan10.downpour.internal.di.DownloaderGraph
import java.io.File

public object Downpour {
    public fun getInstance(
        context: Context,
        config: DownloadManagerConfig = DownloadManagerConfig(),
    ): DownloadManager = DownloaderGraph.getInstance(context, config).downloadManager

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
