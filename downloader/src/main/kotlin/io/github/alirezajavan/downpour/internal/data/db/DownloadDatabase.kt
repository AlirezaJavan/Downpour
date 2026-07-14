package io.github.alirezajavan.downpour.internal.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [DownloadEntity::class, DownloadPartEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
internal abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val NAME = "downpour.db"
    }
}
