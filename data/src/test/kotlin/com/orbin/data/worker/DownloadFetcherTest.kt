package com.orbin.data.worker

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadFetcherTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var dest: java.io.File
    private val fetcher = DownloadFetcher(OkHttpClient.Builder().build())

    @Before
    fun setUp() {
        server.start()
        dest = tmp.newFile("clip.part")
        dest.delete()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun url(): String = server.url("/clip.webm").toString()

    private fun fetch(): DownloadFetchResult = runBlocking { fetcher.fetch(url(), emptyMap(), dest) }

    @Test
    fun aSuccessful200WritesTheWholeBody() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/webm")
                .setBody("webm-bytes"),
        )

        val result = fetch()

        assertThat(result).isEqualTo(DownloadFetchResult.Complete("video/webm"))
        assertThat(dest.readText()).isEqualTo("webm-bytes")
        assertThat(server.takeRequest().getHeader("Range")).isNull()
    }

    @Test
    fun a206AppendsToThePartialFile() {
        dest.writeBytes(byteArrayOf(1, 2, 3))
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 3-5/6")
                .setHeader("Content-Type", "video/mp4")
                .setBody("abc"),
        )

        val result = fetch()

        assertThat(result).isEqualTo(DownloadFetchResult.Complete("video/mp4"))
        assertThat(dest.readBytes().toList()).isEqualTo(listOf<Byte>(1, 2, 3, 97, 98, 99))
        assertThat(server.takeRequest().getHeader("Range")).isEqualTo("bytes=3-")
    }

    @Test
    fun a200OnAPartialFileRestartsTheFile() {
        dest.writeText("OLD")
        server.enqueue(MockResponse().setResponseCode(200).setBody("NEW-CONTENT"))

        val result = fetch()

        assertThat(result).isInstanceOf(DownloadFetchResult.Complete::class.java)
        assertThat(dest.readText()).isEqualTo("NEW-CONTENT")
        assertThat(server.takeRequest().getHeader("Range")).isEqualTo("bytes=3-")
    }

    @Test
    fun a416WithAMatchingTotalLeavesThePartialAsComplete() {
        dest.writeBytes(ByteArray(10) { it.toByte() })
        server.enqueue(
            MockResponse()
                .setResponseCode(416)
                .setHeader("Content-Range", "bytes */10"),
        )

        val result = fetch()

        assertThat(result).isInstanceOf(DownloadFetchResult.Complete::class.java)
        assertThat(dest.length()).isEqualTo(10)
    }

    @Test
    fun a416WithAShorterTotalRestartsFromTheBeginning() {
        dest.writeBytes(ByteArray(10) { 7 })
        server.enqueue(
            MockResponse()
                .setResponseCode(416)
                .setHeader("Content-Range", "bytes */4"),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("abcd"))

        val result = fetch()

        assertThat(result).isInstanceOf(DownloadFetchResult.Complete::class.java)
        assertThat(dest.readText()).isEqualTo("abcd")
        assertThat(server.takeRequest().getHeader("Range")).isEqualTo("bytes=10-")
        assertThat(server.takeRequest().getHeader("Range")).isNull()
    }

    @Test
    fun a429IsRetryableAndHonoursRetryAfter() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "20"))

        assertThat(fetch()).isEqualTo(DownloadFetchResult.Retry(httpCode = 429, retryAfterSeconds = 20L))
        assertThat(dest.exists()).isFalse()
    }

    @Test
    fun a503AndA408AreRetryable() {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "8"))
        assertThat(fetch()).isEqualTo(DownloadFetchResult.Retry(httpCode = 503, retryAfterSeconds = 8L))

        // OkHttp retries a 408 once, so the first response is consumed internally; a second
        // 408 is what the caller sees. Both outcomes (408 or a subsequent IOException) are retryable.
        server.enqueue(MockResponse().setResponseCode(408))
        server.enqueue(MockResponse().setResponseCode(408))
        assertThat(fetch()).isEqualTo(DownloadFetchResult.Retry(httpCode = 408, retryAfterSeconds = null))
    }

    @Test
    fun a404AndA403ArePermanentFailures() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertThat(fetch()).isEqualTo(DownloadFetchResult.Fail(httpCode = 404))

        server.enqueue(MockResponse().setResponseCode(403))
        assertThat(fetch()).isEqualTo(DownloadFetchResult.Fail(httpCode = 403))
    }

    @Test
    fun aDroppedConnectionIsRetryableAndKeepsThePartialFile() {
        dest.writeBytes(byteArrayOf(1, 2, 3))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = fetch()

        assertThat(result).isEqualTo(DownloadFetchResult.Retry(httpCode = null, retryAfterSeconds = null))
        assertThat(dest.readBytes().toList()).isEqualTo(listOf<Byte>(1, 2, 3))
    }

    @Test
    fun aMidBodyDisconnectIsRetryable() {
        dest.writeBytes(byteArrayOf(9))
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 1-1024/1025")
                .setBody("x".repeat(1024))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )

        val result = fetch()

        assertThat(result).isEqualTo(DownloadFetchResult.Retry(httpCode = null, retryAfterSeconds = null))
        // The partial from before the request is still there, possibly with some extra bytes
        // that arrived before the drop; it must not be deleted.
        assertThat(dest.exists()).isTrue()
        assertThat(dest.length()).isAtLeast(1)
    }
}
