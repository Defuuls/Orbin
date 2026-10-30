package com.orbin.network.interceptor

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RetryAfterTrackerClockTest {
    private var now = 1_000_000L

    @Before
    fun setUp() {
        RetryAfterTracker.clear()
        RetryAfterTracker.clock = { now }
    }

    @After
    fun tearDown() {
        RetryAfterTracker.clock = System::currentTimeMillis
        RetryAfterTracker.clear()
    }

    @Test
    fun `a block lapses exactly when its window ends`() {
        RetryAfterTracker.record(HOST, URL, 10)

        now += 9_999
        assertThat(RetryAfterTracker.blockedUntilMs(HOST, URL)).isNotNull()
        now += 1
        assertThat(RetryAfterTracker.blockedUntilMs(HOST, URL)).isNull()
    }

    @Test
    fun `a zero Retry-After blocks nothing`() {
        RetryAfterTracker.record(HOST, URL, 0)

        assertThat(RetryAfterTracker.blockedUntilMs(HOST, URL)).isNull()
    }

    @Test
    fun `clear forgets url and host blocks`() {
        (1..3).forEach { RetryAfterTracker.record(HOST, "$BASE/$it.webm", 30) }

        RetryAfterTracker.clear()

        assertThat(RetryAfterTracker.blockedUntilMs(HOST, "$BASE/1.webm")).isNull()
        assertThat(RetryAfterTracker.blockedUntilMs(HOST, "$BASE/9.webm")).isNull()
    }

    @Test
    fun `refusals more than thirty seconds apart do not escalate to the host`() {
        RetryAfterTracker.record(HOST, "$BASE/1.webm", 5)
        now += 31_000
        RetryAfterTracker.record(HOST, "$BASE/2.webm", 5)
        now += 31_000
        RetryAfterTracker.record(HOST, "$BASE/3.webm", 5)

        assertThat(RetryAfterTracker.blockedUntilMs(HOST, "$BASE/9.webm")).isNull()
    }

    @Test
    fun `the same url refused repeatedly does not escalate to the host`() {
        repeat(5) { RetryAfterTracker.record(HOST, URL, 5) }

        assertThat(RetryAfterTracker.blockedUntilMs(HOST, "$BASE/other.webm")).isNull()
    }

    @Test
    fun `concurrent refusals of distinct urls always escalate the host`() {
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val jobs =
                (1..8).map { id ->
                    pool.submit {
                        start.await()
                        RetryAfterTracker.record(HOST, "$BASE/$id.webm", 30)
                    }
                }
            start.countDown()
            jobs.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertThat(RetryAfterTracker.blockedUntilMs(HOST, "$BASE/unrelated.webm")).isNotNull()
    }

    private companion object {
        const val HOST = "i.4cdn.org"
        const val BASE = "https://i.4cdn.org/g"
        const val URL = "$BASE/1.webm"
    }
}
