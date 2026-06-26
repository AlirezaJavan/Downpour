package io.github.alirezajavan.downpour.internal.engine

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RateLimiterTest {
    @Test
    fun `isUnlimited returns true for zero or negative limit`() {
        assertThat(RateLimiter(0).isUnlimited).isTrue()
        assertThat(RateLimiter(-1).isUnlimited).isTrue()
        assertThat(RateLimiter(100).isUnlimited).isFalse()
    }

    @Test
    fun `acquire returns immediately when unlimited`() =
        runTest {
            val limiter = RateLimiter(0)
            limiter.acquire(1000) // Should not suspend
        }
}
