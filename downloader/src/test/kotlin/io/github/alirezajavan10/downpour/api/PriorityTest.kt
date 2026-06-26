package io.github.alirezajavan10.downpour.api

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PriorityTest {
    @Test
    fun `priority levels are in correct order`() {
        assertThat(Priority.LOW.ordinal).isLessThan(Priority.NORMAL.ordinal)
        assertThat(Priority.NORMAL.ordinal).isLessThan(Priority.HIGH.ordinal)
    }
}
