package com.orbin.media.video

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RateLimitRetryPolicyTest {
    private fun policy(jitter: Long = 0L) =
        RateLimitRetryPolicy(maxAutoRetries = 3, floorMs = 5_000L, maxJitterMs = 3_000L, jitter = { jitter })

    @Test
    fun `the server's wait is honoured when it is longer than the floor`() {
        assertThat(policy().delayMs(0, retryAfterSeconds = 20)).isEqualTo(20_000L)
    }

    @Test
    fun `a short Retry-After is raised to the floor`() {
        assertThat(policy().delayMs(0, retryAfterSeconds = 1)).isEqualTo(5_000L)
        assertThat(policy().delayMs(0, retryAfterSeconds = 0)).isEqualTo(5_000L)
        assertThat(policy().delayMs(0, retryAfterSeconds = -3)).isEqualTo(5_000L)
    }

    @Test
    fun `jitter is added on top`() {
        assertThat(policy(jitter = 2_500L).delayMs(0, retryAfterSeconds = 10)).isEqualTo(12_500L)
    }

    @Test
    fun `jitter is asked for no more than its maximum`() {
        var asked = -1L
        RateLimitRetryPolicy(maxJitterMs = 3_000L, jitter = {
            asked = it
            0L
        }).delayMs(0, 10)

        assertThat(asked).isEqualTo(3_000L)
    }

    @Test
    fun `the default jitter stays within bounds`() {
        val policy = RateLimitRetryPolicy()
        repeat(200) {
            assertThat(policy.delayMs(0, retryAfterSeconds = 10)).isIn(10_000L..13_000L)
        }
    }

    @Test
    fun `it gives up after the third automatic retry`() {
        val policy = policy()

        assertThat(policy.delayMs(0, 10)).isNotNull()
        assertThat(policy.delayMs(1, 10)).isNotNull()
        assertThat(policy.delayMs(2, 10)).isNotNull()
        assertThat(policy.delayMs(3, 10)).isNull()
        assertThat(policy.delayMs(10, 10)).isNull()
    }
}
