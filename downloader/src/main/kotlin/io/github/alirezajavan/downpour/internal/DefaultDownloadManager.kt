package io.github.alirezajavan.downpour.internal

import io.github.alirezajavan.downpour.api.DiagnosticReport
import io.github.alirezajavan.downpour.api.DownloadItem
import io.github.alirezajavan.downpour.api.DownloadListener
import io.github.alirezajavan.downpour.api.DownloadManager
import io.github.alirezajavan.downpour.api.DownloadManagerConfig
import io.github.alirezajavan.downpour.api.DownloadRequest
import io.github.alirezajavan.downpour.api.GroupProgress
import io.github.alirezajavan.downpour.api.Priority
import io.github.alirezajavan.downpour.internal.data.DownloadRepository
import io.github.alirezajavan.downpour.internal.data.toDiagnosticReport
import io.github.alirezajavan.downpour.internal.data.toEntity
import io.github.alirezajavan.downpour.internal.engine.DownloadEngine
import io.github.alirezajavan.downpour.internal.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

internal class DefaultDownloadManager(
    private val repository: DownloadRepository,
    private val engine: DownloadEngine,
    private val scope: CoroutineScope,
    private val config: DownloadManagerConfig,
    private val logger: Logger,
    private val eventDispatcher: DownloadEventDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) : DownloadManager {
    override fun enqueue(request: DownloadRequest): String {
        logger.d("Enqueuing request: $request")
        val intercepted =
            config.interceptors.fold(request) { req, interceptor ->
                interceptor.intercept(req)
            }
        val id = idProvider()
        logger.i("Enqueued download $id for URL: ${intercepted.url}")
        scope.launch {
            repository.insert(intercepted.toEntity(id, clock()))
            engine.onEnqueued()
        }
        return id
    }

    override fun enqueueAll(requests: List<DownloadRequest>): List<String> = requests.map { enqueue(it) }

    override suspend fun setPriority(
        id: String,
        priority: Priority,
    ): Unit = engine.setPriority(id, priority.ordinal)

    override suspend fun moveToFront(id: String): Unit = engine.moveToFront(id)

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

    override suspend fun getByTag(tag: String): List<DownloadItem> = repository.getItemsByTag(tag)

    override fun observe(id: String): Flow<DownloadItem?> = repository.observeItem(id)

    override fun observeAll(): Flow<List<DownloadItem>> = repository.observeAllItems()

    override fun observeByTag(tag: String): Flow<List<DownloadItem>> = repository.observeItemsByTag(tag)

    override fun observeGroupProgress(tag: String): Flow<GroupProgress> = repository.observeGroupProgress(tag)

    override fun addListener(listener: DownloadListener): Unit = eventDispatcher.add(listener)

    override fun removeListener(listener: DownloadListener): Unit = eventDispatcher.remove(listener)
}
