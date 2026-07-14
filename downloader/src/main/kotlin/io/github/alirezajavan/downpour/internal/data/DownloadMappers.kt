package io.github.alirezajavan.downpour.internal.data

import io.github.alirezajavan.downpour.api.ConflictStrategy
import io.github.alirezajavan.downpour.api.DiagnosticReport
import io.github.alirezajavan.downpour.api.DownloadDestination
import io.github.alirezajavan.downpour.api.DownloadItem
import io.github.alirezajavan.downpour.api.DownloadProgress
import io.github.alirezajavan.downpour.api.DownloadRequest
import io.github.alirezajavan.downpour.api.DownloadState
import io.github.alirezajavan.downpour.api.GroupProgress
import io.github.alirezajavan.downpour.api.Priority
import io.github.alirezajavan.downpour.internal.data.db.DownloadEntity

internal fun DownloadRequest.toEntity(
    id: String,
    now: Long,
): DownloadEntity =
    DownloadEntity(
        id = id,
        url = url,
        mirrors = mirrors,
        destinationPath =
            when (destination) {
                is DownloadDestination.File -> destination.path
                is DownloadDestination.Uri -> destination.uriString
            },
        destinationType =
            when (destination) {
                is DownloadDestination.File -> 0
                is DownloadDestination.Uri -> 1
            },
        headers = headers,
        metadata = metadata,
        tag = tag,
        workerClass = workerClass,
        priority = priority.ordinal,
        sortKey = now,
        conflictStrategy = conflictStrategy.ordinal,
        networkType = networkType.ordinal,
        requiresCharging = requiresCharging,
        requiresBatteryNotLow = requiresBatteryNotLow,
        requiresStorageNotLow = requiresStorageNotLow,
        scheduleStartMinuteOfDay = scheduleStartMinuteOfDay,
        scheduleEndMinuteOfDay = scheduleEndMinuteOfDay,
        scheduledAtMillis = scheduledAtMillis,
        maxConnections = maxConnections,
        maxBytesPerSecond = maxBytesPerSecond,
        checksumAlgorithm = checksum?.algorithm?.ordinal,
        checksumValue = checksum?.expectedHex,
        maxRetries = retryPolicy.maxRetries,
        initialBackoffMillis = retryPolicy.initialBackoff.inWholeMilliseconds,
        backoffMultiplier = retryPolicy.backoffMultiplier,
        maxBackoffMillis = retryPolicy.maxBackoff.inWholeMilliseconds,
        status = DownloadStatus.QUEUED,
        downloadedBytes = 0,
        totalBytes = DownloadProgress.UNKNOWN,
        bytesPerSecond = 0,
        etaMillis = DownloadProgress.UNKNOWN,
        supportsResume = false,
        etag = null,
        lastModified = null,
        retryCount = 0,
        errorType = null,
        errorMessage = null,
        errorHttpCode = null,
        createdAtMillis = now,
        updatedAtMillis = now,
    )

internal fun DownloadEntity.toItem(): DownloadItem =
    DownloadItem(
        id = id,
        url = url,
        destination =
            when (destinationType) {
                0 -> DownloadDestination.File(destinationPath)
                else -> DownloadDestination.Uri(destinationPath)
            },
        state = toState(),
        priority = Priority.entries[priority],
        conflictStrategy = ConflictStrategy.entries[conflictStrategy],
        tag = tag,
        metadata = metadata,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

internal fun DownloadEntity.toState(): DownloadState =
    when (status) {
        DownloadStatus.QUEUED -> {
            DownloadState.Queued
        }

        DownloadStatus.RUNNING -> {
            DownloadState.Running(toProgress())
        }

        DownloadStatus.PAUSED -> {
            DownloadState.Paused(toProgress())
        }

        DownloadStatus.COMPLETED -> {
            DownloadState.Completed(
                destination =
                    if (destinationType == 0) {
                        DownloadDestination.File(destinationPath)
                    } else {
                        DownloadDestination.Uri(destinationPath)
                    },
                totalBytes = totalBytes,
            )
        }

        DownloadStatus.CANCELLED -> {
            DownloadState.Cancelled
        }

        DownloadStatus.WAITING_FOR_NETWORK -> {
            DownloadState.WaitingForNetwork
        }

        DownloadStatus.SCHEDULED -> {
            DownloadState.Scheduled
        }

        DownloadStatus.FAILED -> {
            DownloadState.Failed(
                ErrorCodec.decode(errorType, errorMessage, errorHttpCode),
            )
        }
    }

internal fun DownloadEntity.toProgress(): DownloadProgress =
    DownloadProgress(
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        bytesPerSecond = bytesPerSecond,
        etaMillis = etaMillis,
    )

internal fun List<DownloadEntity>.toGroupProgress(): GroupProgress =
    GroupProgress(
        total = size,
        completed = count { it.status == DownloadStatus.COMPLETED },
        failed = count { it.status == DownloadStatus.FAILED },
        running = count { it.status == DownloadStatus.RUNNING },
        queued = count { it.status == DownloadStatus.QUEUED },
        downloadedBytes = sumOf { it.downloadedBytes },
        totalBytes = sumOf { it.totalBytes.coerceAtLeast(0) },
    )

internal fun DownloadEntity.toDiagnosticReport(): DiagnosticReport =
    DiagnosticReport(
        id = id,
        url = url,
        state = toState(),
        retryCount = retryCount,
        lastError =
            if (status == DownloadStatus.FAILED) {
                ErrorCodec.decode(errorType, errorMessage, errorHttpCode)
            } else {
                null
            },
        isResumeSupported = supportsResume,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        etag = etag,
        lastModified = lastModified,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
