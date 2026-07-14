package io.github.alirezajavan.downpour.internal.data

import io.github.alirezajavan.downpour.api.DownloadError
import io.github.alirezajavan.downpour.api.DownloadItem
import io.github.alirezajavan.downpour.api.GroupProgress
import io.github.alirezajavan.downpour.internal.data.db.DownloadDao
import io.github.alirezajavan.downpour.internal.data.db.DownloadEntity
import io.github.alirezajavan.downpour.internal.data.db.DownloadPartEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DownloadRepository(
    private val dao: DownloadDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun insert(entity: DownloadEntity) = dao.upsert(entity)

    suspend fun getEntity(id: String): DownloadEntity? = dao.getById(id)

    suspend fun getItem(id: String): DownloadItem? = dao.getById(id)?.toItem()

    suspend fun getAllItems(): List<DownloadItem> = dao.getAll().map { it.toItem() }

    fun observeItem(id: String): Flow<DownloadItem?> = dao.observeById(id).map { it?.toItem() }

    fun observeAllItems(): Flow<List<DownloadItem>> = dao.observeAll().map { entities -> entities.map { it.toItem() } }

    suspend fun nextQueued(limit: Int): List<DownloadEntity> =
        dao.getByStatuses(
            listOf(
                DownloadStatus.QUEUED,
                DownloadStatus.WAITING_FOR_NETWORK,
                DownloadStatus.SCHEDULED,
            ),
            limit,
        )

    suspend fun runningEntities(): List<DownloadEntity> = dao.getByStatuses(listOf(DownloadStatus.RUNNING), Int.MAX_VALUE)

    suspend fun entitiesByStatuses(statuses: List<DownloadStatus>): List<DownloadEntity> = dao.getByStatuses(statuses, Int.MAX_VALUE)

    suspend fun runningCount(): Int = dao.countByStatus(DownloadStatus.RUNNING)

    suspend fun setStatus(
        id: String,
        status: DownloadStatus,
    ) = dao.updateStatus(id, status, clock())

    suspend fun setDestinationPath(
        id: String,
        path: String,
    ) = dao.updateDestinationPath(id, path, clock())

    /** Persists the final destination and marks it resolved (one-time, never re-renamed). */
    suspend fun markDestinationResolved(
        id: String,
        path: String,
    ) = dao.markDestinationResolved(id, path, clock())

    suspend fun setStatusIn(
        from: List<DownloadStatus>,
        to: DownloadStatus,
        excludeIds: List<String> = emptyList(),
    ) = if (excludeIds.isEmpty()) {
        dao.updateStatusInExcept(from, to, listOf(""), clock())
    } else {
        dao.updateStatusInExcept(from, to, excludeIds, clock())
    }

    suspend fun getByTag(tag: String): List<DownloadEntity> = dao.getByTag(tag)

    suspend fun getItemsByTag(tag: String): List<DownloadItem> = dao.getByTag(tag).map { it.toItem() }

    fun observeItemsByTag(tag: String): Flow<List<DownloadItem>> = dao.observeByTag(tag).map { entities -> entities.map { it.toItem() } }

    fun observeGroupProgress(tag: String): Flow<GroupProgress> = dao.observeByTag(tag).map { it.toGroupProgress() }

    suspend fun setPriority(
        id: String,
        priority: Int,
    ) = dao.updatePriority(id, priority, clock())

    suspend fun setEffectiveConnections(
        id: String,
        connections: Int,
    ) = dao.updateEffectiveConnections(id, connections, clock())

    suspend fun moveToFront(id: String) {
        val front = (dao.minSortKey() ?: clock()) - 1
        dao.updateSortKey(id, front, clock())
    }

    suspend fun deleteExpiredCompleted(ttlMillis: Long) = dao.deleteCompletedBefore(DownloadStatus.COMPLETED, clock() - ttlMillis)

    suspend fun setStatusByTag(
        tag: String,
        from: List<DownloadStatus>,
        to: DownloadStatus,
    ) = dao.updateStatusByTag(tag, from, to, clock())

    suspend fun deleteByTag(tag: String) = dao.deleteByTag(tag)

    suspend fun setProgress(
        id: String,
        downloaded: Long,
        total: Long,
        speed: Long,
        eta: Long,
    ) = dao.updateProgress(id, downloaded, total, speed, eta, clock())

    /**
     * Progress write that is silently ignored unless the row is still RUNNING. The download task
     * uses this (never [setProgress]) so it can never advance a row that the engine has already
     * paused/cancelled/completed.
     */
    suspend fun setRunningProgress(
        id: String,
        downloaded: Long,
        total: Long,
        speed: Long,
        eta: Long,
    ) = dao.updateProgressIfRunning(id, downloaded, total, speed, eta, clock(), DownloadStatus.RUNNING)

    /**
     * True if a different, non-terminal download already targets [path]. Used to keep concurrent
     * downloads of the same URL from colliding on one file.
     */
    suspend fun isDestinationClaimedByOther(
        path: String,
        excludeId: String,
    ): Boolean = dao.countOthersUsingDestination(path, excludeId, TERMINAL_STATUSES) > 0

    suspend fun setResumeMetadata(
        id: String,
        supportsResume: Boolean,
        total: Long,
        etag: String?,
        lastModified: String?,
    ) = dao.updateResumeMetadata(id, supportsResume, total, etag, lastModified, clock())

    suspend fun setError(
        id: String,
        error: DownloadError,
        retryCount: Int,
    ) {
        val encoded = ErrorCodec.encode(error)
        dao.updateError(
            id = id,
            status = DownloadStatus.FAILED,
            errorType = encoded.type,
            errorMessage = encoded.message,
            httpCode = encoded.httpCode,
            retryCount = retryCount,
            now = clock(),
        )
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun replaceParts(parts: List<DownloadPartEntity>) {
        if (parts.isEmpty()) return
        dao.deleteParts(parts.first().downloadId)
        dao.insertParts(parts)
    }

    suspend fun getParts(downloadId: String): List<DownloadPartEntity> = dao.getParts(downloadId)

    suspend fun setPartOffset(
        partId: Long,
        offset: Long,
    ) = dao.updatePartOffset(partId, offset)

    suspend fun clearParts(downloadId: String) = dao.deleteParts(downloadId)

    private companion object {
        // A row in one of these states no longer "owns" its destination path.
        val TERMINAL_STATUSES = listOf(DownloadStatus.COMPLETED, DownloadStatus.CANCELLED, DownloadStatus.FAILED)
    }
}
