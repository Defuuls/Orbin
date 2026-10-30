package com.orbin.media.preload

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RequestThrottlerTest {
    private fun throttler(
        maxConcurrent: Int = 1,
        maxPerMinute: Int = 0,
    ) = RequestThrottler(maxConcurrent = maxConcurrent, delayBetweenRequests = 0, maxPerMinute = maxPerMinute)

    /** True when [RequestThrottler.acquire] returned inside [millis]. */
    private fun RequestThrottler.acquiresWithin(millis: Long = 300L): Boolean =
        runBlocking { withTimeoutOrNull(millis) { acquire() } } != null

    @Test
    fun `requests go straight through when nothing is limiting them`() {
        val throttler = throttler()

        assertThat(throttler.acquiresWithin()).isTrue()
        throttler.release()
        assertThat(throttler.acquiresWithin()).isTrue()
    }

    @Test
    fun `a 429 starts a cooldown`() {
        val throttler = throttler()

        throttler.recordResponse(429, retryAfter = "30")

        assertThat(throttler.acquiresWithin()).isFalse()
    }

    @Test
    fun `a 503 starts a cooldown`() {
        val throttler = throttler()

        throttler.recordResponse(503, retryAfter = null)

        assertThat(throttler.acquiresWithin()).isFalse()
    }

    @Test
    fun `other statuses do not`() {
        val throttler = throttler()

        listOf(200, 404, 500).forEach { throttler.recordResponse(it, retryAfter = "30") }

        assertThat(throttler.acquiresWithin()).isTrue()
    }

    @Test
    fun `even a Retry-After of zero waits the floor`() {
        val throttler = throttler()

        throttler.recordResponse(429, retryAfter = "0")

        assertThat(throttler.acquiresWithin()).isFalse()
    }

    @Test
    fun `the per minute cap holds the next request back`() {
        val throttler = throttler(maxConcurrent = 2, maxPerMinute = 1)

        assertThat(throttler.acquiresWithin()).isTrue()
        throttler.release()
        assertThat(throttler.acquiresWithin()).isFalse()
    }

    @Test
    fun `a request abandoned while waiting returns its permit`() {
        val throttler = throttler(maxConcurrent = 1)
        throttler.recordResponse(429, retryAfter = "30")
        assertThat(throttler.acquiresWithin()).isFalse()

        // The permit is back, so releasing again would exceed the semaphore's limit.
        assertThrows(IllegalStateException::class.java) { throttler.release() }
    }

    @Test
    fun `concurrency is capped by released permits`() {
        val throttler = throttler(maxConcurrent = 1)

        assertThat(throttler.acquiresWithin()).isTrue()
        assertThat(throttler.acquiresWithin()).isFalse()
        throttler.release()
        assertThat(throttler.acquiresWithin()).isTrue()
    }
}
