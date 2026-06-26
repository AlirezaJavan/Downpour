package io.github.alirezajavan.downpour.internal.data.db

import androidx.room.TypeConverter
import io.github.alirezajavan.downpour.api.ConflictStrategy
import io.github.alirezajavan.downpour.internal.data.DownloadStatus
import kotlinx.serialization.json.Json

internal class Converters {
    @TypeConverter
    fun fromStringMap(value: Map<String, String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStringMap(value: String): Map<String, String> = if (value.isEmpty()) emptyMap() else json.decodeFromString(value)

    @TypeConverter
    fun fromStatus(status: DownloadStatus): Int = status.ordinal

    @TypeConverter
    fun toStatus(ordinal: Int): DownloadStatus = DownloadStatus.entries[ordinal]

    @TypeConverter
    fun fromConflictStrategy(strategy: ConflictStrategy): Int = strategy.ordinal

    @TypeConverter
    fun toConflictStrategy(ordinal: Int): ConflictStrategy = ConflictStrategy.entries[ordinal]

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
