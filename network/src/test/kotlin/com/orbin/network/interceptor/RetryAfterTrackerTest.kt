package com.orbin.network.interceptor

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class RetryAfterTrackerTest {
    @After
    fun tearDown() = RetryAfterTracker.clear()

    @Test
    fun `one refused video does not block the rest of the host`() {
        RetryAfterTracker.record(HOST, "$BASE/1.webm", 30)

        assertThat(RetryAfterTracker.blockedUntilMs(HOST, "$BASE/1.webm")).isNotNull()
        assertThat(RetryAfterTracker.blockedUntilMs(HOST, "$BASE/2.webm")).isNull()
    }

    @Test
    fun `several refused videos block the host`() {
        (1..3).forEach { RetryAfterTracker.record(HOST, "$BASE/$it.webm", 30) }

        assertThat(RetryAfterTracker.blockedUntilMs(HOST, "$BASE/9.webm")).isNotNull()
    }

    @Test
    fun `a huge Retry-After is capped to a minute`() {
        RetryAfterTracker.record(HOST, "$BASE/1.webm", 3_600)

        val until = RetryAfterTracker.blockedUntilMs(HOST, "$BASE/1.webm")!!
        assertThat(until - System.currentTimeMillis()).isAtMost(60_000L)
    }

    private companion object {
        const val HOST = "i.4cdn.org"
        const val BASE = "https://i.4cdn.org/g"
    }
}
