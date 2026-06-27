package io.github.alirezajavan.downpour.internal.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import io.github.alirezajavan.downpour.internal.data.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
internal interface DownloadDao {
    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observeById(id: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY createdAtMillis DESC")
    suspend fun getAll(): List<DownloadEntity>

    @Query(
        "SELECT * FROM downloads WHERE status IN (:statuses) " +
            "ORDER BY priority DESC, createdAtMillis ASC LIMIT :limit",
    )
    suspend fun getByStatuses(
        statuses: List<DownloadStatus>,
        limit: Int,
    ): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = :status")
    suspend fun countByStatus(status: DownloadStatus): Int

    @Query("UPDATE downloads SET status = :status, updatedAtMillis = :now WHERE id = :id")
    suspend fun updateStatus(
        id: String,
        status: DownloadStatus,
        now: Long,
    )

    @Query("UPDATE downloads SET destinationPath = :path, updatedAtMillis = :now WHERE id = :id")
    suspend fun updateDestinationPath(
        id: String,
        path: String,
        now: Long,
    )

    /** Persists the final destination and marks it resolved so it is never recomputed/renamed. */
    @Query(
        "UPDATE downloads SET destinationPath = :path, destinationResolved = 1, updatedAtMillis = :now WHERE id = :id",
    )
    suspend fun markDestinationResolved(
        id: String,
        path: String,
        now: Long,
    )

    @Query(
        "UPDATE downloads SET status = :to, updatedAtMillis = :now " +
            "WHERE status IN (:from) AND id NOT IN (:excludeIds)",
    )
    suspend fun updateStatusInExcept(
        from: List<DownloadStatus>,
        to: DownloadStatus,
        excludeIds: List<String>,
        now: Long,
    )

    @Query(
        "UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, " +
            "bytesPerSecond = :speed, etaMillis = :eta, updatedAtMillis = :now WHERE id = :id",
    )
    suspend fun updateProgress(
        id: String,
        downloaded: Long,
        total: Long,
        speed: Long,
        eta: Long,
        now: Long,
    )

    /**
     * Progress write that only applies while the row is still RUNNING. Used by the download task so
     * that a late/in-flight progress flush can never advance a row the engine has already moved to
     * PAUSED/CANCELLED/COMPLETED (the "Paused at 10% and climbing" bug).
     */
    @Query(
        "UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, " +
            "bytesPerSecond = :speed, etaMillis = :eta, updatedAtMillis = :now " +
            "WHERE id = :id AND status = :running",
    )
    suspend fun updateProgressIfRunning(
        id: String,
        downloaded: Long,
        total: Long,
        speed: Long,
        eta: Long,
        now: Long,
        running: DownloadStatus,
    )

    /**
     * Number of OTHER downloads (any non-terminal state) already targeting [path]. Lets the task
     * detect that a concurrent download of the same URL has already claimed a destination, even
     * before its file exists on disk, so both can be given distinct filenames.
     */
    @Query(
        "SELECT COUNT(*) FROM downloads WHERE destinationPath = :path AND id != :excludeId " +
            "AND status NOT IN (:terminal)",
    )
    suspend fun countOthersUsingDestination(
        path: String,
        excludeId: String,
        terminal: List<DownloadStatus>,
    ): Int

    @Query(
        "UPDATE downloads SET supportsResume = :supportsResume, totalBytes = :total, " +
            "etag = :etag, lastModified = :lastModified, updatedAtMillis = :now WHERE id = :id",
    )
    suspend fun updateResumeMetadata(
        id: String,
        supportsResume: Boolean,
        total: Long,
        etag: String?,
        lastModified: String?,
        now: Long,
    )

    @Query(
        "UPDATE downloads SET status = :status, errorType = :errorType, " +
            "errorMessage = :errorMessage, errorHttpCode = :httpCode, retryCount = :retryCount, " +
            "updatedAtMillis = :now WHERE id = :id",
    )
    suspend fun updateError(
        id: String,
        status: DownloadStatus,
        errorType: Int,
        errorMessage: String?,
        httpCode: Int?,
        retryCount: Int,
        now: Long,
    )

    @Query("SELECT * FROM downloads WHERE tag = :tag")
    suspend fun getByTag(tag: String): List<DownloadEntity>

    @Query("UPDATE downloads SET status = :status, updatedAtMillis = :now WHERE tag = :tag AND status IN (:from)")
    suspend fun updateStatusByTag(
        tag: String,
        from: List<DownloadStatus>,
        status: DownloadStatus,
        now: Long,
    )

    @Query("DELETE FROM downloads WHERE tag = :tag")
    suspend fun deleteByTag(tag: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParts(parts: List<DownloadPartEntity>)

    @Query("SELECT * FROM download_parts WHERE downloadId = :downloadId ORDER BY `index` ASC")
    suspend fun getParts(downloadId: String): List<DownloadPartEntity>

    @Query("UPDATE download_parts SET currentOffset = :offset WHERE id = :partId")
    suspend fun updatePartOffset(
        partId: Long,
        offset: Long,
    )

    @Query("DELETE FROM download_parts WHERE downloadId = :downloadId")
    suspend fun deleteParts(downloadId: String)
}
