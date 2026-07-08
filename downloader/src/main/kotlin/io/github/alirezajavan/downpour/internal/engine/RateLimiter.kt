package io.github.alirezajavan.downpour.internal.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

internal class RateLimiter(
    private val bytesPerSecond: Long,
    private val nanoClock: () -> Long = System::nanoTime,
) {
    private val mutex = Mutex()
    private var availableTokens = bytesPerSecond.toDouble()
    private var lastRefillNanos = nanoClock()

    val isUnlimited: Boolean
        get() = bytesPerSecond <= 0

    suspend fun acquire(bytes: Int) {
        if (isUnlimited) return
        val waitMillis = mutex.withLock { reserve(bytes) }
        if (waitMillis > 0) delay(waitMillis.milliseconds)
    }

    private fun reserve(bytes: Int): Long {
        refill()
        // Deliberately left negative (not clamped to 0) when it goes into deficit: clamping would
        // forgive debt run up by concurrent callers (multiple parts sharing this limiter) between
        // this reservation and the delay it prescribes, letting aggregate throughput exceed the cap
        // by roughly the connection count. Leaving the true debt in place means the next refill has
        // to pay it off before granting anyone new tokens, so the cap holds regardless of how many
        // callers are reserving concurrently.
        availableTokens -= bytes
        if (availableTokens >= 0) return 0
        return (-availableTokens / bytesPerSecond * MILLIS_PER_SECOND).toLong()
    }

    private fun refill() {
        val now = nanoClock()
        val elapsedSeconds = (now - lastRefillNanos) / NANOS_PER_SECOND
        availableTokens =
            minOf(
                bytesPerSecond.toDouble(),
                availableTokens + elapsedSeconds * bytesPerSecond,
            )
        lastRefillNanos = now
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000.0
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
