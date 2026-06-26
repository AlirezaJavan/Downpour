package io.github.alirezajavan.downpour.internal.data.db

import com.google.common.truth.Truth.assertThat
import io.github.alirezajavan.downpour.api.ConflictStrategy
import io.github.alirezajavan.downpour.internal.data.DownloadStatus
import org.junit.jupiter.api.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `maps to and from string map`() {
        val map = mapOf("key" to "value")
        val string = converters.fromStringMap(map)
        val result = converters.toStringMap(string)

        assertThat(result).isEqualTo(map)
    }

    @Test
    fun `maps to and from status`() {
        DownloadStatus.entries.forEach { status ->
            val ordinal = converters.fromStatus(status)
            val result = converters.toStatus(ordinal)
            assertThat(result).isEqualTo(status)
        }
    }

    @Test
    fun `maps to and from conflict strategy`() {
        ConflictStrategy.entries.forEach { strategy ->
            val ordinal = converters.fromConflictStrategy(strategy)
            val result = converters.toConflictStrategy(ordinal)
            assertThat(result).isEqualTo(strategy)
        }
    }
}
