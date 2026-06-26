package io.github.alirezajavan10.downpour.internal.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "download_parts",
    foreignKeys = [
        ForeignKey(
            entity = DownloadEntity::class,
            parentColumns = ["id"],
            childColumns = ["downloadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("downloadId")],
)
internal data class DownloadPartEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val downloadId: String,
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    val currentOffset: Long,
)
