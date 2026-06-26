package io.github.alirezajavan10.downpour.internal.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.alirezajavan10.downpour.internal.data.DownloadStatus

@Entity(tableName = "downloads")
internal data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val destinationPath: String,
    val destinationType: Int,
    val headers: Map<String, String>,
    val metadata: Map<String, String>,
    val tag: String?,
    val workerClass: String?,
    val priority: Int,
    val conflictStrategy: Int,
    val networkType: Int,
    val maxConnections: Int,
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
