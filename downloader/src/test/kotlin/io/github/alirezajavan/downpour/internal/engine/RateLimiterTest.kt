package io.github.alirezajavan.downpour.internal.engine

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `concurrent callers sharing a limiter do not exceed the aggregate cap`() =
        runTest {
            // Regression test: reserve() used to clamp a deficit back to 0 tokens instead of
            // leaving the true debt in place, which forgave whatever a concurrent caller had
            // already borrowed before its delay elapsed. With N callers hammering the same
            // limiter that let aggregate throughput run to roughly N times the configured cap.
            val bytesPerSecond = 1_000_000L
            val limiter = RateLimiter(bytesPerSecond, nanoClock = { currentTime * 1_000_000 })
            val chunk = 65_536
            val chunksPerCaller = 60
            val callerCount = 4

            val jobs =
                List(callerCount) {
                    launch {
                        repeat(chunksPerCaller) { limiter.acquire(chunk) }
                    }
                }
            jobs.joinAll()

            val totalBytes = callerCount.toLong() * chunksPerCaller * chunk
            val elapsedSeconds = currentTime / 1000.0
            val achievedBytesPerSecond = totalBytes / elapsedSeconds

            // Allow slack for the one-second initial burst capacity, but nowhere near the ~4x
            // overshoot the clamp-to-zero bug produced with 4 concurrent callers.
            assertThat(achievedBytesPerSecond).isLessThan(bytesPerSecond * 1.5)
        }
}
