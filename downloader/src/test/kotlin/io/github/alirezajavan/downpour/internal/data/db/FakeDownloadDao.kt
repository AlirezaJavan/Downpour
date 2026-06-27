package io.github.alirezajavan.downpour.internal.data.db

import io.github.alirezajavan.downpour.internal.data.DownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory [DownloadDao] that mirrors the real Room query semantics (ordering, status gating,
 * destination-claim counting). Lets the engine's state machine be tested against a faithful store
 * with fully deterministic virtual time — no Room executor, no Robolectric.
 */
internal class FakeDownloadDao : DownloadDao {
    private val rows = linkedMapOf<String, DownloadEntity>()
    private val parts = mutableListOf<DownloadPartEntity>()
    private val partIds = AtomicLong(1)
    private val emissions = MutableStateFlow<List<DownloadEntity>>(emptyList())

    private fun publish() {
        emissions.value = rows.values.toList()
    }

    override suspend fun upsert(entity: DownloadEntity) {
        rows[entity.id] = entity
        publish()
    }

    override suspend fun getById(id: String): DownloadEntity? = rows[id]

    override fun observeById(id: String): Flow<DownloadEntity?> = emissions.map { it.firstOrNull { row -> row.id == id } }

    override fun observeAll(): Flow<List<DownloadEntity>> = emissions.map { sortedByCreatedDesc(it) }

    override suspend fun getAll(): List<DownloadEntity> = sortedByCreatedDesc(rows.values.toList())

    override suspend fun getByStatuses(
        statuses: List<DownloadStatus>,
        limit: Int,
    ): List<DownloadEntity> =
        rows.values
            .filter { it.status in statuses }
            .sortedWith(compareByDescending<DownloadEntity> { it.priority }.thenBy { it.createdAtMillis })
            .take(limit)

    override suspend fun countByStatus(status: DownloadStatus): Int = rows.values.count { it.status == status }

    override suspend fun updateStatus(
        id: String,
        status: DownloadStatus,
        now: Long,
    ) {
        rows[id]?.let { rows[id] = it.copy(status = status, updatedAtMillis = now) }
        publish()
    }

    override suspend fun updateDestinationPath(
        id: String,
        path: String,
        now: Long,
    ) {
        rows[id]?.let { rows[id] = it.copy(destinationPath = path, updatedAtMillis = now) }
        publish()
    }

    override suspend fun markDestinationResolved(
        id: String,
        path: String,
        now: Long,
    ) {
        rows[id]?.let { rows[id] = it.copy(destinationPath = path, destinationResolved = true, updatedAtMillis = now) }
        publish()
    }

    override suspend fun updateStatusInExcept(
        from: List<DownloadStatus>,
        to: DownloadStatus,
        excludeIds: List<String>,
        now: Long,
    ) {
        rows.values.toList().forEach {
            if (it.status in from && it.id !in excludeIds) {
                rows[it.id] = it.copy(status = to, updatedAtMillis = now)
            }
        }
        publish()
    }

    override suspend fun updateProgress(
        id: String,
        downloaded: Long,
        total: Long,
        speed: Long,
        eta: Long,
        now: Long,
    ) {
        rows[id]?.let {
            rows[id] =
                it.copy(downloadedBytes = downloaded, totalBytes = total, bytesPerSecond = speed, etaMillis = eta, updatedAtMillis = now)
        }
        publish()
    }

    override suspend fun updateProgressIfRunning(
        id: String,
        downloaded: Long,
        total: Long,
        speed: Long,
        eta: Long,
        now: Long,
        running: DownloadStatus,
    ) {
        val row = rows[id] ?: return
        if (row.status != running) return
        rows[id] =
            row.copy(downloadedBytes = downloaded, totalBytes = total, bytesPerSecond = speed, etaMillis = eta, updatedAtMillis = now)
        publish()
    }

    override suspend fun countOthersUsingDestination(
        path: String,
        excludeId: String,
        terminal: List<DownloadStatus>,
    ): Int = rows.values.count { it.destinationPath == path && it.id != excludeId && it.status !in terminal }

    override suspend fun updateResumeMetadata(
        id: String,
        supportsResume: Boolean,
        total: Long,
        etag: String?,
        lastModified: String?,
        now: Long,
    ) {
        rows[id]?.let {
            rows[id] =
                it.copy(
                    supportsResume = supportsResume,
                    totalBytes = total,
                    etag = etag,
                    lastModified = lastModified,
                    updatedAtMillis = now,
                )
        }
        publish()
    }

    override suspend fun updateError(
        id: String,
        status: DownloadStatus,
        errorType: Int,
        errorMessage: String?,
        httpCode: Int?,
        retryCount: Int,
        now: Long,
    ) {
        rows[id]?.let {
            rows[id] =
                it.copy(
                    status = status,
                    errorType = errorType,
                    errorMessage = errorMessage,
                    errorHttpCode = httpCode,
                    retryCount = retryCount,
                    updatedAtMillis = now,
                )
        }
        publish()
    }

    override suspend fun getByTag(tag: String): List<DownloadEntity> = rows.values.filter { it.tag == tag }

    override suspend fun updateStatusByTag(
        tag: String,
        from: List<DownloadStatus>,
        status: DownloadStatus,
        now: Long,
    ) {
        rows.values.toList().forEach {
            if (it.tag == tag && it.status in from) {
                rows[it.id] = it.copy(status = status, updatedAtMillis = now)
            }
        }
        publish()
    }

    override suspend fun deleteByTag(tag: String) {
        rows.values.filter { it.tag == tag }.forEach { rows.remove(it.id) }
        publish()
    }

    override suspend fun delete(id: String) {
        rows.remove(id)
        publish()
    }

    override suspend fun insertParts(parts: List<DownloadPartEntity>) {
        parts.forEach { part ->
            val withId = if (part.id == 0L) part.copy(id = partIds.getAndIncrement()) else part
            this.parts.add(withId)
        }
    }

    override suspend fun getParts(downloadId: String): List<DownloadPartEntity> =
        parts
            .filter {
                it.downloadId == downloadId
            }.sortedBy { it.index }

    override suspend fun updatePartOffset(
        partId: Long,
        offset: Long,
    ) {
        val index = parts.indexOfFirst { it.id == partId }
        if (index >= 0) parts[index] = parts[index].copy(currentOffset = offset)
    }

    override suspend fun deleteParts(downloadId: String) {
        parts.removeAll { it.downloadId == downloadId }
    }

    private fun sortedByCreatedDesc(list: List<DownloadEntity>): List<DownloadEntity> = list.sortedByDescending { it.createdAtMillis }
}
