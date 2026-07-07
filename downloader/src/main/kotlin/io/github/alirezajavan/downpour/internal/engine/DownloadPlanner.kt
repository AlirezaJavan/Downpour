package io.github.alirezajavan.downpour.internal.engine

import io.github.alirezajavan.downpour.api.DownloadManagerConfig
import io.github.alirezajavan.downpour.internal.data.DownloadRepository
import io.github.alirezajavan.downpour.internal.data.db.DownloadEntity
import io.github.alirezajavan.downpour.internal.data.db.DownloadPartEntity
import io.github.alirezajavan.downpour.internal.network.RemoteFileInfo

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
        val requestedConnections = if (activeConnections > 0) activeConnections else entity.maxConnections

        if (!shouldUseMultiConnection(info, requestedConnections)) {
            return singleConnectionPlan(info, ifRange, fileLength)
        }
        val resumed = resumePlanOrNull(entity, info, ifRange)
        if (resumed != null) {
            if (activeConnections > 0 && resumed.parts.size != requestedConnections) {
                return adjustConnections(entity, info, resumed.parts, requestedConnections)
            }
            return resumed
        }
        return freshMultiConnectionPlan(entity, info, ifRange, requestedConnections)
    }

    private suspend fun adjustConnections(
        entity: DownloadEntity,
        info: RemoteFileInfo,
        parts: List<PartPlan>,
        target: Int,
    ): DownloadPlan {
        val currentEntities = repository.getParts(entity.id)
        return if (target > parts.size) {
            scaleUp(entity, info, currentEntities, target)
        } else {
            scaleDown(entity, info, currentEntities, target)
        }
    }

    private suspend fun scaleUp(
        entity: DownloadEntity,
        info: RemoteFileInfo,
        parts: List<DownloadPartEntity>,
        target: Int,
    ): DownloadPlan {
        val newParts = parts.toMutableList()
        while (newParts.size < target && newParts.size < MAX_PARTS) {
            val partToSplit =
                newParts
                    .filter { it.currentOffset < it.endByte || it.endByte == OPEN_ENDED }
                    .maxByOrNull {
                        if (it.endByte == OPEN_ENDED) Long.MAX_VALUE else it.endByte - it.currentOffset
                    } ?: break

            val remaining =
                if (partToSplit.endByte == OPEN_ENDED) {
                    DEFAULT_SPLIT_SIZE
                } else {
                    partToSplit.endByte - partToSplit.currentOffset
                }

            if (remaining < config.minSizeForMultiConnection / 2) break

            val splitPoint = partToSplit.currentOffset + (remaining / 2)
            newParts.remove(partToSplit)
            newParts.add(partToSplit.copy(endByte = splitPoint))
            newParts.add(
                DownloadPartEntity(
                    downloadId = entity.id,
                    index = 0, // Will re-index below
                    startByte = splitPoint + 1,
                    endByte = partToSplit.endByte,
                    currentOffset = splitPoint + 1,
                ),
            )
        }

        val reindexed =
            newParts.sortedBy { it.startByte }.mapIndexed { index, part ->
                part.copy(index = index)
            }
        repository.replaceParts(reindexed)
        return DownloadPlan(
            info.totalBytes,
            reindexed.map { it.toPartPlan() },
            info.etag ?: info.lastModified,
            isMultiConnection = true,
        )
    }

    private suspend fun scaleDown(
        entity: DownloadEntity,
        info: RemoteFileInfo,
        parts: List<DownloadPartEntity>,
        target: Int,
    ): DownloadPlan {
        val newParts = parts.sortedBy { it.startByte }.toMutableList()
        while (newParts.size > target && newParts.size > 1) {
            val last = newParts.removeAt(newParts.size - 1)
            val secondLast = newParts.removeAt(newParts.size - 1)

            val merged =
                secondLast.copy(
                    endByte = last.endByte,
                    currentOffset =
                        if (secondLast.currentOffset <= secondLast.endByte) {
                            secondLast.currentOffset
                        } else {
                            last.currentOffset
                        },
                )
            newParts.add(merged)
        }

        val reindexed =
            newParts.sortedBy { it.startByte }.mapIndexed { index, part ->
                part.copy(index = index)
            }
        repository.replaceParts(reindexed)
        return DownloadPlan(
            info.totalBytes,
            reindexed.map { it.toPartPlan() },
            info.etag ?: info.lastModified,
            isMultiConnection = true,
        )
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
        const val DEFAULT_SPLIT_SIZE = 1024L * 1024 * 100 // 100MB
    }
}
