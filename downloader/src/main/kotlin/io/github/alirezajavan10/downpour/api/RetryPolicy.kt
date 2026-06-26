package io.github.alirezajavan10.downpour.api

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public data class RetryPolicy(
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val initialBackoff: Duration = DEFAULT_INITIAL_BACKOFF,
    val backoffMultiplier: Double = DEFAULT_BACKOFF_MULTIPLIER,
    val maxBackoff: Duration = DEFAULT_MAX_BACKOFF,
) {
    init {
        require(maxRetries >= 0) { "maxRetries must be >= 0" }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier must be >= 1.0" }
    }

    public fun backoffFor(attempt: Int): Duration {
        val scaled = initialBackoff * backoffMultiplier.pow(attempt)
        return minOf(scaled, maxBackoff)
    }

    public companion object {
        public const val DEFAULT_MAX_RETRIES: Int = 3
        public const val DEFAULT_BACKOFF_MULTIPLIER: Double = 2.0
        public val DEFAULT_INITIAL_BACKOFF: Duration = 2.seconds
        public val DEFAULT_MAX_BACKOFF: Duration = 60.seconds

        public val NONE: RetryPolicy = RetryPolicy(maxRetries = 0)
    }
}

private fun Double.pow(exponent: Int): Double {
    var result = 1.0
    repeat(exponent) { result *= this }
    return result
}
