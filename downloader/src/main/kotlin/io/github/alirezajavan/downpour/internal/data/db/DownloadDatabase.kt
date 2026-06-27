package io.github.alirezajavan.downpour.internal.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DownloadEntity::class, DownloadPartEntity::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
internal abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val NAME = "downpour.db"

        // Adds the destinationResolved flag (see DownloadEntity) so a download's final filename is
        // computed exactly once and never re-renamed on restart.
        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE downloads ADD COLUMN destinationResolved INTEGER NOT NULL DEFAULT 0")
                }
            }
    }
}
