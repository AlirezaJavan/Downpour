package io.github.alirezajavan.downpour.api

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class RetryPolicyTest {
    @Test
    fun `backoff grows geometrically from the initial value`() {
        val policy = RetryPolicy(initialBackoff = 2.seconds, backoffMultiplier = 2.0)

        assertThat(policy.backoffFor(0)).isEqualTo(2.seconds)
        assertThat(policy.backoffFor(1)).isEqualTo(4.seconds)
        assertThat(policy.backoffFor(2)).isEqualTo(8.seconds)
    }

    @Test
    fun `backoff is capped at the configured maximum`() {
        val policy =
            RetryPolicy(
                initialBackoff = 2.seconds,
                backoffMultiplier = 2.0,
                maxBackoff = 10.seconds,
            )

        assertThat(policy.backoffFor(10)).isEqualTo(10.seconds)
    }

    @Test
    fun `negative retry counts are rejected`() {
        runCatching { RetryPolicy(maxRetries = -1) }
            .also { assertThat(it.isFailure).isTrue() }
    }

    @Test
    fun `default values are reasonable`() {
        val policy = RetryPolicy()
        assertThat(policy.maxRetries).isEqualTo(3)
    }
}
