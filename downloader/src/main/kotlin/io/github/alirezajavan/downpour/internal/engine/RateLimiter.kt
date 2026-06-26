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
        availableTokens -= bytes
        if (availableTokens >= 0) return 0
        val deficit = -availableTokens
        availableTokens = 0.0
        return (deficit / bytesPerSecond * MILLIS_PER_SECOND).toLong()
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
