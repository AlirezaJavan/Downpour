package io.github.alirezajavan.downpour.api

import kotlinx.coroutines.flow.Flow

public interface DownloadManager {
    public fun enqueue(request: DownloadRequest): String

    public suspend fun pause(id: String)

    public suspend fun resume(id: String)

    public suspend fun cancel(id: String)

    public suspend fun retry(id: String)

    public suspend fun remove(
        id: String,
        deleteFile: Boolean = false,
    )

    public suspend fun pauseAll()

    public suspend fun resumeAll()

    public suspend fun cancelAll()

    public suspend fun pauseByTag(tag: String)

    public suspend fun resumeByTag(tag: String)

    public suspend fun cancelByTag(tag: String)

    public suspend fun removeByTag(
        tag: String,
        deleteFiles: Boolean = false,
    )

    public suspend fun get(id: String): DownloadItem?

    public suspend fun getDiagnosticReport(id: String): DiagnosticReport?

    public suspend fun getAll(): List<DownloadItem>

    public fun observe(id: String): Flow<DownloadItem?>

    public fun observeAll(): Flow<List<DownloadItem>>
}
