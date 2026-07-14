package io.github.alirezajavan.downpour.api

import kotlinx.serialization.Serializable

@Serializable
public data class SerializableRetryPolicy(
    val maxRetries: Int,
    val initialBackoffMillis: Long,
    val backoffMultiplier: Double,
    val maxBackoffMillis: Long,
)

@Serializable
public data class SerializableChecksum(
    val algorithm: String,
    val expectedHex: String,
)

@Serializable
public data class SerializableDownloadSchedule(
    val startTimeMillis: Long?,
    val endTimeMillis: Long?,
)

@Serializable
public data class QueueSnapshotItem(
    val url: String,
    val destinationPath: String,
    val destinationType: Int, // 0 for File, 1 for Uri
    val headers: Map<String, String>,
    val priority: String,
    val conflictStrategy: String,
    val networkType: String,
    val maxConnections: Int,
    val retryPolicy: SerializableRetryPolicy,
    val maxBytesPerSecond: Long,
    val checksum: SerializableChecksum?,
    val tag: String?,
    val workerClass: String?,
    val metadata: Map<String, String>,
    val mirrors: List<String>,
    val requiresCharging: Boolean,
    val requiresBatteryNotLow: Boolean,
    val requiresStorageNotLow: Boolean,
    val schedule: SerializableDownloadSchedule,
)

@Serializable
public data class QueueSnapshot(
    val items: List<QueueSnapshotItem>,
)
