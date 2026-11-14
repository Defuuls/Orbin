package com.orbin.media.video

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class VideoServerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var upstream: MockWebServer
    private lateinit var cacheDir: File
    private lateinit var server: VideoServer

    @Before
    fun setUp() {
        upstream = MockWebServer()
        cacheDir = tempFolder.newFolder("video_cache")
        server =
            VideoServer(
                cacheDirProvider = { cacheDir },
                client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build(),
            )
    }

    @After
    fun tearDown() {
        server.close()
        upstream.shutdown()
    }

    @Test
    fun streamsAndCachesToDiskAndPlaysFromDiskOnSecondOpen() =
        runBlocking {
            val payload = ByteArray(64 * 1024) { (it % 251).toByte() }
            val hits = AtomicInteger(0)
            upstream.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        hits.incrementAndGet()
                        return MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "video/webm")
                            .setBody(Buffer().write(payload))
                    }
                }
            upstream.start()
            val url = upstream.url("/clip.webm").toString()

            val firstSource = server.source(url)
            assertThat(firstSource).startsWith("http://127.0.0.1:")

            val downloaded =
                (URL(firstSource).openConnection(Proxy.NO_PROXY) as HttpURLConnection).run {
                    inputStream.readBytes()
                }
            assertThat(downloaded).isEqualTo(payload)

            // Wait briefly for final rename on disk.
            repeat(20) {
                if (server.cachedFile(url) != null) return@repeat
                delay(25)
            }
            assertThat(server.cachedFile(url)).isNotNull()

            val secondSource = server.source(url)
            assertThat(secondSource).startsWith("file:")
            assertThat(hits.get()).isEqualTo(1)
        }

    @Test
    fun servesByteRangesIncludingFarAheadRanges() =
        runBlocking {
            val payload = ByteArray(512 * 1024) { (it % 199).toByte() }
            upstream.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val range = request.getHeader("Range")
                        if (range != null && range.startsWith("bytes=")) {
                            val parts = range.removePrefix("bytes=").split("-")
                            val start = parts[0].toInt()
                            val end = parts.getOrNull(1)?.toIntOrNull() ?: (payload.size - 1)
                            val clampedEnd = minOf(end, payload.size - 1)
                            val slice = payload.copyOfRange(start, clampedEnd + 1)
                            return MockResponse()
                                .setResponseCode(206)
                                .setHeader("Content-Type", "video/webm")
                                .setHeader("Content-Range", "bytes $start-$clampedEnd/${payload.size}")
                                .setBody(Buffer().write(slice))
                        }
                        return MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "video/webm")
                            .setBody(Buffer().write(payload))
                    }
                }
            upstream.start()
            val url = upstream.url("/seek.webm").toString()

            val localUrl = server.source(url)
            val tailStart = payload.size - 1024
            val tailEnd = payload.size - 1
            val conn = (URL(localUrl).openConnection(Proxy.NO_PROXY) as HttpURLConnection)
            conn.setRequestProperty("Range", "bytes=$tailStart-$tailEnd")
            assertThat(conn.responseCode).isEqualTo(206)
            val tailBytes = conn.inputStream.readBytes()
            assertThat(tailBytes).isEqualTo(payload.copyOfRange(tailStart, payload.size))
        }

    @Test
    fun reportsCacheSizeTrimsOldestAndClears() =
        runBlocking {
            val f1 = File(cacheDir, "a.webm").apply { writeBytes(ByteArray(1024)) }
            f1.setLastModified(1_000L)
            val f2 = File(cacheDir, "b.webm").apply { writeBytes(ByteArray(2048)) }
            f2.setLastModified(2_000L)

            assertThat(server.cacheSize()).isEqualTo(3072L)

            server.setMaxCacheBytes(2048L)
            assertThat(f1.exists()).isFalse()
            assertThat(f2.exists()).isTrue()
            assertThat(server.cacheSize()).isEqualTo(2048L)

            server.clearCache()
            assertThat(server.cacheSize()).isEqualTo(0L)
            assertThat(f2.exists()).isFalse()
        }
}
