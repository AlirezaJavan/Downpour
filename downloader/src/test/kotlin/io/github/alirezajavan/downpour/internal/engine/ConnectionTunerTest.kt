package io.github.alirezajavan.downpour.internal.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ConnectionTunerTest {
    @Test
    fun `decide initially increases connections`() {
        val tuner = ConnectionTuner(minConnections = 1, maxConnections = 4)
        val next = tuner.decide(currentConnections = 2, currentSpeed = 1000)
        assertThat(next).isEqualTo(3)
    }

    @Test
    fun `decide keeps increasing if speed improves significantly`() {
        val tuner = ConnectionTuner(minConnections = 1, maxConnections = 4)

        // Initial increase
        tuner.decide(currentConnections = 2, currentSpeed = 1000)

        // Speed improved from 1000 at 2 connections to 1600 at 3 connections
        // Expected linear gain was 500 (1000/2). Actual gain is 600.
        val next = tuner.decide(currentConnections = 3, currentSpeed = 1600)
        assertThat(next).isEqualTo(4)
    }

    @Test
    fun `decide stops increasing if speed plateaus`() {
        val tuner = ConnectionTuner(minConnections = 1, maxConnections = 4)

        // Initial increase
        tuner.decide(currentConnections = 2, currentSpeed = 1000)

        // Speed slightly improved: 1000 -> 1100 at 3 connections.
        // Expected gain was 500. Actual gain 100.
        val next = tuner.decide(currentConnections = 3, currentSpeed = 1100)
        assertThat(next).isEqualTo(3)
    }

    @Test
    fun `decide reduces if speed drops significantly`() {
        val tuner = ConnectionTuner(minConnections = 1, maxConnections = 4)

        // Initial increase
        tuner.decide(currentConnections = 2, currentSpeed = 1000)

        // Speed dropped to 500 at 3 connections.
        val next = tuner.decide(currentConnections = 3, currentSpeed = 500)
        assertThat(next).isEqualTo(2)
    }

    @Test
    fun `decide respects maxConnections`() {
        val tuner = ConnectionTuner(minConnections = 1, maxConnections = 4)
        val next = tuner.decide(currentConnections = 4, currentSpeed = 1000)
        assertThat(next).isEqualTo(4)
    }
}
