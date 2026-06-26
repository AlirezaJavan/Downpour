package io.github.alirezajavan10.downpour.internal

import io.github.alirezajavan10.downpour.api.DiagnosticReport
import io.github.alirezajavan10.downpour.api.DownloadItem
import io.github.alirezajavan10.downpour.api.DownloadManager
import io.github.alirezajavan10.downpour.api.DownloadManagerConfig
import io.github.alirezajavan10.downpour.api.DownloadRequest
import io.github.alirezajavan10.downpour.internal.data.DownloadRepository
import io.github.alirezajavan10.downpour.internal.data.toDiagnosticReport
import io.github.alirezajavan10.downpour.internal.data.toEntity
import io.github.alirezajavan10.downpour.internal.engine.DownloadEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

internal class DefaultDownloadManager(
    private val repository: DownloadRepository,
    private val engine: DownloadEngine,
    private val scope: CoroutineScope,
    private val config: DownloadManagerConfig,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) : DownloadManager {
    override fun enqueue(request: DownloadRequest): String {
        val intercepted =
            config.interceptors.fold(request) { req, interceptor ->
                interceptor.intercept(req)
            }
        val id = idProvider()
        scope.launch {
            repository.insert(intercepted.toEntity(id, clock()))
            engine.onEnqueued()
        }
        return id
    }

    override suspend fun pause(id: String): Unit = engine.pause(id)

    override suspend fun resume(id: String): Unit = engine.resume(id)

    override suspend fun cancel(id: String): Unit = engine.cancel(id)

    override suspend fun retry(id: String): Unit = engine.retry(id)

    override suspend fun remove(
        id: String,
        deleteFile: Boolean,
    ): Unit = engine.remove(id, deleteFile)

    override suspend fun pauseAll(): Unit = engine.pauseAll()

    override suspend fun resumeAll(): Unit = engine.resumeAll()

    override suspend fun cancelAll(): Unit = engine.cancelAll()

    override suspend fun pauseByTag(tag: String): Unit = engine.pauseByTag(tag)

    override suspend fun resumeByTag(tag: String): Unit = engine.resumeByTag(tag)

    override suspend fun cancelByTag(tag: String): Unit = engine.cancelByTag(tag)

    override suspend fun removeByTag(
        tag: String,
        deleteFiles: Boolean,
    ): Unit = engine.removeByTag(tag, deleteFiles)

    override suspend fun get(id: String): DownloadItem? = repository.getItem(id)

    override suspend fun getDiagnosticReport(id: String): DiagnosticReport? = repository.getEntity(id)?.toDiagnosticReport()

    override suspend fun getAll(): List<DownloadItem> = repository.getAllItems()

    override fun observe(id: String): Flow<DownloadItem?> = repository.observeItem(id)

    override fun observeAll(): Flow<List<DownloadItem>> = repository.observeAllItems()
}
