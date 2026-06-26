package io.github.alirezajavan10.downpour.internal.service

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ByteFormatterTest {
    @Test
    fun `formatSpeed formats correctly`() {
        assertThat(ByteFormatter.formatSpeed(0)).isEqualTo("")
        assertThat(ByteFormatter.formatSpeed(-100)).isEqualTo("")
        assertThat(ByteFormatter.formatSpeed(500)).isEqualTo("500.0 B/s")
        assertThat(ByteFormatter.formatSpeed(1024)).isEqualTo("1.0 KB/s")
        assertThat(ByteFormatter.formatSpeed(1024 * 1024)).isEqualTo("1.0 MB/s")
        assertThat(ByteFormatter.formatSpeed(1024L * 1024 * 1024)).isEqualTo("1.0 GB/s")
    }

    @Test
    fun `formatSize formats correctly`() {
        assertThat(ByteFormatter.formatSize(0)).isEqualTo("0 B")
        assertThat(ByteFormatter.formatSize(-100)).isEqualTo("0 B")
        assertThat(ByteFormatter.formatSize(500)).isEqualTo("500.0 B")
        assertThat(ByteFormatter.formatSize(1024)).isEqualTo("1.0 KB")
        assertThat(ByteFormatter.formatSize(1024 * 1024)).isEqualTo("1.0 MB")
        assertThat(ByteFormatter.formatSize(1024L * 1024 * 1024)).isEqualTo("1.0 GB")
        assertThat(ByteFormatter.formatSize(1024L * 1024 * 1024 * 1024)).isEqualTo("1.0 TB")
    }
}
