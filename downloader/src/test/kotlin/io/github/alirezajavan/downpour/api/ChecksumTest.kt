package io.github.alirezajavan.downpour.api

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ChecksumTest {
    @Test
    fun `checksum matches correctly`() {
        val checksum = Checksum(ChecksumAlgorithm.SHA256, "abcd")
        assertThat(checksum.matches("abcd")).isTrue()
        assertThat(checksum.matches("ABCD")).isTrue()
        assertThat(checksum.matches("1234")).isFalse()
    }
}
