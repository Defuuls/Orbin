package com.orbin.network.interceptor

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class VideoRetryAfterInterceptorTest {
    private val server = MockWebServer()
    private val client =
        OkHttpClient
            .Builder()
            .addInterceptor(VideoRetryAfterInterceptor(VideoRequestPacer(spacingMs = 0)))
            .build()

    @Before
    fun setUp() {
        RetryAfterTracker.clear()
    }

    @After
    fun tearDown() {
        server.shutdown()
        RetryAfterTracker.clear()
    }

    private fun get(path: String) = client.newCall(Request.Builder().url(server.url(path)).build()).execute()

    private fun blockedSeconds(path: String): Long? {
        val url = server.url(path)
        val until = RetryAfterTracker.blockedUntilMs(url.host, url.toString()) ?: return null
        return (until - System.currentTimeMillis()) / 1_000L
    }

    private fun assertBlockedBetween(
        path: String,
        min: Long,
        max: Long,
    ) {
        val seconds = blockedSeconds(path)
        assertThat(seconds).isNotNull()
        assertThat(seconds).isAtLeast(min)
        assertThat(seconds).isAtMost(max)
    }

    @Test
    fun `a 429 blocks the url and the next call never reaches the server`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "20"))
        server.start()

        get("/a.webm").close()
        val second = get("/a.webm")

        assertThat(second.code).isEqualTo(429)
        assertThat(second.message).contains("cached")
        assertThat(server.requestCount).isEqualTo(1)
        assertBlockedBetween("/a.webm", 18L, 20L)
    }

    @Test
    fun `a 429 without Retry-After blocks for the default`() {
        server.enqueue(MockResponse().setResponseCode(429))
        server.start()

        get("/a.webm").close()

        assertBlockedBetween("/a.webm", RetryAfter.DEFAULT_SECONDS - 2, RetryAfter.DEFAULT_SECONDS)
    }

    @Test
    fun `an http date Retry-After is honoured`() {
        val inThirty = ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(30)
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", DateTimeFormatter.RFC_1123_DATE_TIME.format(inThirty)),
        )
        server.start()

        get("/a.webm").close()

        assertBlockedBetween("/a.webm", 27L, 30L)
    }

    @Test
    fun `a refused video leaves other videos on the host working`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))
        server.enqueue(MockResponse().setBody("ok"))
        server.start()

        get("/a.webm").close()
        val other = get("/b.webm")

        assertThat(other.code).isEqualTo(200)
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `a locally refused call does not extend the block`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "40"))
        server.start()

        get("/a.webm").close()
        val before = blockedSeconds("/a.webm")
        repeat(3) { get("/a.webm").close() }

        assertThat(blockedSeconds("/a.webm")).isAtMost(before)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `successful responses record nothing`() {
        server.enqueue(MockResponse().setBody("ok"))
        server.start()

        get("/a.webm").close()

        assertThat(blockedSeconds("/a.webm")).isNull()
    }
}
