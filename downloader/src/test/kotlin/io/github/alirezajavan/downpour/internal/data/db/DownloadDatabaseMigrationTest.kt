package io.github.alirezajavan.downpour.internal.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadDatabaseMigrationTest {
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration =
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(VERSION_4) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE downloads (id TEXT NOT NULL PRIMARY KEY, createdAtMillis INTEGER NOT NULL)",
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()
        db = FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    @Test
    fun `migration 4 to 5 adds new columns and seeds sortKey from createdAtMillis`() {
        db.execSQL("INSERT INTO downloads (id, createdAtMillis) VALUES ('a', 123)")

        DownloadDatabase.MIGRATION_4_5.migrate(db)

        db.query("SELECT sortKey, mirrors, requiresCharging FROM downloads WHERE id = 'a'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(123)
            assertThat(cursor.getString(1)).isEqualTo("[]")
            assertThat(cursor.getInt(2)).isEqualTo(0)
        }
    }

    @Test
    fun `migration 5 to 6 adds effectiveConnections defaulting to -1`() {
        db.execSQL("INSERT INTO downloads (id, createdAtMillis) VALUES ('a', 123)")

        DownloadDatabase.MIGRATION_5_6.migrate(db)

        db.query("SELECT effectiveConnections FROM downloads WHERE id = 'a'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(-1)
        }
    }

    @Test
    fun `migration 6 to 7 adds scheduling columns defaulting to null`() {
        db.execSQL("INSERT INTO downloads (id, createdAtMillis) VALUES ('a', 123)")

        DownloadDatabase.MIGRATION_6_7.migrate(db)

        db.query("SELECT scheduleStartMinuteOfDay, scheduleEndMinuteOfDay FROM downloads WHERE id = 'a'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.isNull(0)).isTrue()
            assertThat(cursor.isNull(1)).isTrue()
        }
    }

    private companion object {
        const val VERSION_4 = 4
    }
}
