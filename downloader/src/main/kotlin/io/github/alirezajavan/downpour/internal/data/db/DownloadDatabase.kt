package io.github.alirezajavan.downpour.internal.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DownloadEntity::class, DownloadPartEntity::class],
    version = 8,
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

        // Adds fallback mirrors, device-state constraints, and an explicit queue sortKey (seeded
        // from createdAtMillis so existing ordering is preserved, then mutable via moveToFront).
        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE downloads ADD COLUMN mirrors TEXT NOT NULL DEFAULT '[]'")
                    db.execSQL("ALTER TABLE downloads ADD COLUMN sortKey INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE downloads SET sortKey = createdAtMillis")
                    db.execSQL("ALTER TABLE downloads ADD COLUMN requiresCharging INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE downloads ADD COLUMN requiresBatteryNotLow INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE downloads ADD COLUMN requiresStorageNotLow INTEGER NOT NULL DEFAULT 0")
                }
            }

        // Adds effectiveConnections, tracking the adaptive-concurrency tuner's current connection
        // count so recovery after process death resumes with the last known-good value instead of
        // always restarting from maxConnections. -1 means "not yet tuned".
        val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE downloads ADD COLUMN effectiveConnections INTEGER NOT NULL DEFAULT -1")
                }
            }

        // Adds scheduling window support. nullable Ints (scheduleStartMinuteOfDay,
        // scheduleEndMinuteOfDay) representing the local time window for the download.
        val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE downloads ADD COLUMN scheduleStartMinuteOfDay INTEGER")
                    db.execSQL("ALTER TABLE downloads ADD COLUMN scheduleEndMinuteOfDay INTEGER")
                }
            }

        // Adds date-based scheduling support (scheduledAtMillis).
        val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE downloads ADD COLUMN scheduledAtMillis INTEGER")
                }
            }
    }
}
