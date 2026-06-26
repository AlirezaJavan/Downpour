package io.github.alirezajavan10.downpour.internal.engine

import io.github.alirezajavan10.downpour.api.DownloadManagerConfig
import io.github.alirezajavan10.downpour.internal.data.DownloadRepository
import io.github.alirezajavan10.downpour.internal.data.db.DownloadEntity
import io.github.alirezajavan10.downpour.internal.data.db.DownloadPartEntity
import io.github.alirezajavan10.downpour.internal.network.RemoteFileInfo

internal class DownloadPlanner(
    private val repository: DownloadRepository,
    private val config: DownloadManagerConfig,
) {
    suspend fun plan(
        entity: DownloadEntity,
        info: RemoteFileInfo,
        fileLength: Long,
        activeConnections: Int = -1,
    ): DownloadPlan {
        val ifRange = info.etag ?: info.lastModified
        val connections = if (activeConnections > 0) activeConnections else entity.maxConnections

        if (!shouldUseMultiConnection(info, connections)) {
            return singleConnectionPlan(info, ifRange, fileLength)
        }
        val resumed = resumePlanOrNull(entity, info, ifRange)
        return resumed ?: freshMultiConnectionPlan(entity, info, ifRange, connections)
    }

    private fun shouldUseMultiConnection(
        info: RemoteFileInfo,
        connections: Int,
    ): Boolean =
        info.acceptsRanges &&
            info.hasKnownSize &&
            connections > 1 &&
            info.totalBytes >= config.minSizeForMultiConnection

    private fun singleConnectionPlan(
        info: RemoteFileInfo,
        ifRange: String?,
        fileLength: Long,
    ): DownloadPlan {
        val canResume = info.acceptsRanges && ifRange != null && fileLength > 0
        val downloaded = if (canResume) fileLength else 0
        val end = if (info.hasKnownSize) info.totalBytes - 1 else OPEN_ENDED
        val part = PartPlan(partId = 0, index = 0, start = 0, end = end, downloaded = downloaded)
        return DownloadPlan(info.totalBytes, listOf(part), ifRange, isMultiConnection = false)
    }

    private suspend fun resumePlanOrNull(
        entity: DownloadEntity,
        info: RemoteFileInfo,
        ifRange: String?,
    ): DownloadPlan? {
        if (!isResumeValid(entity, info)) return null
        val persisted = repository.getParts(entity.id)
        if (persisted.isEmpty()) return null
        val parts = persisted.map { it.toPartPlan() }
        return DownloadPlan(info.totalBytes, parts, ifRange, isMultiConnection = true)
    }

    private fun isResumeValid(
        entity: DownloadEntity,
        info: RemoteFileInfo,
    ): Boolean = entity.etag == info.etag && entity.lastModified == info.lastModified

    private suspend fun freshMultiConnectionPlan(
        entity: DownloadEntity,
        info: RemoteFileInfo,
        ifRange: String?,
        connections: Int,
    ): DownloadPlan {
        val actualConnections = connections.coerceAtMost(MAX_PARTS)
        val boundaries = splitRanges(info.totalBytes, actualConnections)
        val toPersist =
            boundaries.mapIndexed { index, range ->
                DownloadPartEntity(
                    downloadId = entity.id,
                    index = index,
                    startByte = range.first,
                    endByte = range.last,
                    currentOffset = range.first,
                )
            }
        repository.replaceParts(toPersist)
        val parts = repository.getParts(entity.id).map { it.toPartPlan() }
        return DownloadPlan(info.totalBytes, parts, ifRange, isMultiConnection = true)
    }

    private fun splitRanges(
        total: Long,
        connections: Int,
    ): List<LongRange> {
        val chunk = total / connections
        return (0 until connections).map { index ->
            val start = index * chunk
            val end = if (index == connections - 1) total - 1 else start + chunk - 1
            start..end
        }
    }

    private fun DownloadPartEntity.toPartPlan(): PartPlan =
        PartPlan(
            partId = id,
            index = index,
            start = startByte,
            end = endByte,
            downloaded = currentOffset - startByte,
        )

    private companion object {
        const val OPEN_ENDED = -1L
        const val MAX_PARTS = 16
    }
}
