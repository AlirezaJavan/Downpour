package io.github.alirezajavan.downpour.internal.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.alirezajavan.downpour.api.DownloadSchedule
import io.github.alirezajavan.downpour.internal.data.DownloadStatus

@Entity(tableName = "downloads")
internal data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val mirrors: List<String> = emptyList(),
    val destinationPath: String,
    val destinationType: Int,
    // True once the final on-disk destination has been resolved (filename + conflict rename). Guards
    // against re-resolving — and thus re-renaming ("file(2)(1).bin") — when a task restarts before
    // any bytes are recorded (e.g. a multi-connection download that preallocated its file).
    val destinationResolved: Boolean = false,
    val headers: Map<String, String>,
    val metadata: Map<String, String>,
    val tag: String?,
    val workerClass: String?,
    val priority: Int,
    val sortKey: Long = 0,
    val conflictStrategy: Int,
    val networkType: Int,
    val requiresCharging: Boolean = false,
    val requiresBatteryNotLow: Boolean = false,
    val requiresStorageNotLow: Boolean = false,
    @Embedded val schedule: DownloadSchedule,
    val maxConnections: Int,
    val effectiveConnections: Int = -1,
    val maxBytesPerSecond: Long,
    val checksumAlgorithm: Int?,
    val checksumValue: String?,
    val maxRetries: Int,
    val initialBackoffMillis: Long,
    val backoffMultiplier: Double,
    val maxBackoffMillis: Long,
    val status: DownloadStatus,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val etaMillis: Long,
    val supportsResume: Boolean,
    val etag: String?,
    val lastModified: String?,
    val retryCount: Int,
    val errorType: Int?,
    val errorMessage: String?,
    val errorHttpCode: Int?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
