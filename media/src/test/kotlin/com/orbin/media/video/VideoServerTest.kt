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
import java.net.Socket
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

    /** A short clip can finish downloading before the player connects to the address it was given. */
    @Test
    fun aStreamThatFinishedBeforeThePlayerConnectedIsStillServed() =
        runBlocking {
            val payload = ByteArray(2048) { (it % 127).toByte() }
            upstream.enqueue(MockResponse().setHeader("Content-Type", "video/webm").setBody(Buffer().write(payload)))
            upstream.start()
            val url = upstream.url("/short.webm").toString()
            val local = URL(server.source(url))
            // Wait until the download has finished and left the active set.
            repeat(200) {
                if (server.source(url).startsWith("file:")) return@repeat
                delay(10)
            }
            assertThat(server.source(url)).startsWith("file:")

            val late = local.openConnection(Proxy.NO_PROXY) as HttpURLConnection

            assertThat(late.responseCode).isEqualTo(200)
            assertThat(late.inputStream.readBytes()).isEqualTo(payload)

            // Once the cache is cleared the old address stops answering with a body.
            server.clearCache()
            assertThat(statusOf(local, local.path)).isEqualTo(404)
        }

    @Test
    fun purgingKeepsOnlyTheVideoThatIsOpen() =
        runBlocking {
            upstream.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        MockResponse().setHeader("Content-Type", "video/webm").setBody(Buffer().write(ByteArray(4096)))
                }
            upstream.start()
            val open = upstream.url("/open.webm").toString()
            val finished = upstream.url("/finished.webm").toString()
            listOf(open, finished).forEach { url ->
                (URL(server.source(url)).openConnection(Proxy.NO_PROXY) as HttpURLConnection).inputStream.readBytes()
                awaitCached(url)
            }
            // The reader paged away from one video; the other is still on screen.
            server.unwatch(finished)
            val stray = File(cacheDir, "left-by-an-older-version.webm").apply { writeBytes(ByteArray(16)) }

            server.purgeUnwatched()

            assertThat(server.cachedFile(open)).isNotNull()
            assertThat(server.cachedFile(finished)).isNull()
            assertThat(stray.exists()).isFalse()
            assertThat(server.progressOf(finished).value).isNull()
            assertThat(server.progressOf(open).value).isEqualTo(1f)
        }

    @Test
    fun purgingAbandonsABackgroundDownloadAndItsPartialFile() =
        runBlocking {
            upstream.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "video/webm")
                    .setBody(Buffer().write(ByteArray(256 * 1024)))
                    // Slow enough that the download is still running when the app is left.
                    .throttleBody(1024, 200, java.util.concurrent.TimeUnit.MILLISECONDS),
            )
            upstream.start()
            val url = upstream.url("/prefetched.webm").toString()
            server.prefetch(url)
            repeat(100) {
                if (cacheDir.listFiles().orEmpty().any { it.name.endsWith(".part") }) return@repeat
                delay(25)
            }
            assertThat(
                cacheDir
                    .listFiles()
                    .orEmpty()
                    .map { it.name }
                    .any { it.endsWith(".part") },
            ).isTrue()

            server.purgeUnwatched()

            // The abandoned download may be mid-write; it removes its own partial file as it stops.
            repeat(100) {
                if (cacheDir.listFiles().orEmpty().isEmpty()) return@repeat
                delay(25)
            }
            assertThat(cacheDir.listFiles().orEmpty().toList()).isEmpty()
            assertThat(server.cachedFile(url)).isNull()
        }

    @Test
    fun purgingAnEmptyCacheIsHarmless() =
        runBlocking {
            server.purgeUnwatched()

            assertThat(server.cacheSize()).isEqualTo(0L)
        }

    private suspend fun awaitCached(url: String) {
        repeat(40) {
            if (server.cachedFile(url) != null) return
            delay(25)
        }
        error("$url was never cached")
    }

    @Test
    fun refusesAStreamWithoutThisProcessToken() =
        runBlocking {
            // Slow, so the stream is still registered while every wrong path is tried.
            upstream.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "video/webm")
                    .setBody(Buffer().write(ByteArray(64 * 1024)))
                    .throttleBody(4096, 100, java.util.concurrent.TimeUnit.MILLISECONDS),
            )
            upstream.start()
            val local = URL(server.source(upstream.url("/guarded.webm").toString()))
            val (token, id) = local.path.trimStart('/').split('/')

            // The id is derived from the public URL, so another app can compute it; not the token.
            assertThat(statusOf(local, "/$id")).isEqualTo(404)
            assertThat(statusOf(local, "/${"0".repeat(token.length)}/$id")).isEqualTo(404)
            assertThat(statusOf(local, "/$token/$id/extra")).isEqualTo(404)
            assertThat(statusOf(local, "/$token/")).isEqualTo(404)
            // Same answer as an id that does not exist: the two cannot be told apart.
            assertThat(statusOf(local, "/$token/${"f".repeat(id.length)}")).isEqualTo(404)
            assertThat(statusOf(local, "/$token/$id")).isEqualTo(200)
        }

    @Test
    fun aNewServerDoesNotReuseAnotherServersToken() =
        runBlocking {
            upstream.enqueue(MockResponse().setBody("a"))
            upstream.enqueue(MockResponse().setBody("b"))
            upstream.start()
            val url = upstream.url("/same.webm").toString()
            val other =
                VideoServer(
                    cacheDirProvider = { tempFolder.newFolder("other_cache") },
                    client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build(),
                )
            try {
                val first = URL(server.source(url)).path.trimStart('/').split('/')
                val second = URL(other.source(url)).path.trimStart('/').split('/')
                assertThat(first[1]).isEqualTo(second[1])
                assertThat(first[0]).isNotEqualTo(second[0])
                assertThat(first[0].length).isAtLeast(32)
            } finally {
                other.close()
            }
        }

    @Test
    fun rejectsAnOversizedRequestLineWithoutBufferingIt() =
        runBlocking {
            upstream.enqueue(MockResponse().setBody("clip"))
            upstream.start()
            val local = URL(server.source(upstream.url("/big.webm").toString()))

            val status = rawStatus(local, "GET /" + "a".repeat(MAX_REQUEST_LINE_BYTES * 2) + " HTTP/1.1\r\n\r\n")

            assertThat(status).isEqualTo(431)
        }

    @Test
    fun rejectsTooManyHeaders() =
        runBlocking {
            upstream.enqueue(MockResponse().setBody("clip"))
            upstream.start()
            val local = URL(server.source(upstream.url("/headers.webm").toString()))
            val headers = (0..MAX_REQUEST_HEADERS).joinToString("") { "X-Filler-$it: 1\r\n" }

            assertThat(rawStatus(local, "GET ${local.path} HTTP/1.1\r\n$headers\r\n")).isEqualTo(431)
        }

    @Test
    fun readsAnOrdinaryRequestAndItsHeaders() {
        val request =
            readLocalRequest(
                "get /tok/abc?x=1 HTTP/1.1\r\nRange: bytes=5-9\r\nHost: 127.0.0.1\r\n\r\n".byteInputStream(),
            )

        val parsed = request as LocalRequest.Parsed
        assertThat(parsed.method).isEqualTo("GET")
        assertThat(parsed.path).isEqualTo("tok/abc")
        assertThat(parsed.headers).containsEntry("range", "bytes=5-9")
        assertThat(parsed.streamId("tok")).isEqualTo("abc")
        assertThat(parsed.streamId("tok2")).isNull()
        assertThat(parsed.streamId("to")).isNull()
    }

    @Test
    fun aClosedOrMalformedConnectionIsNotARequest() {
        assertThat(readLocalRequest("".byteInputStream())).isNull()
        assertThat(readLocalRequest("garbage\r\n\r\n".byteInputStream())).isNull()
    }

    @Test
    fun aBareLineFeedEndsALineLikeACarriageReturnLineFeed() {
        val parsed =
            readLocalRequest(
                "GET /tok/abc HTTP/1.1\nRange: bytes=0-\n\n".byteInputStream(),
            ) as LocalRequest.Parsed

        assertThat(parsed.path).isEqualTo("tok/abc")
        assertThat(parsed.headers).containsEntry("range", "bytes=0-")
    }

    @Test
    fun aRequestLineAtTheLimitIsStillRead() {
        val path = "a".repeat(MAX_REQUEST_LINE_BYTES - "GET / HTTP/1.1".length)
        val atLimit = readLocalRequest("GET /$path HTTP/1.1\r\n\r\n".byteInputStream())
        val overLimit = readLocalRequest("GET /a$path HTTP/1.1\r\n\r\n".byteInputStream())

        assertThat(atLimit).isInstanceOf(LocalRequest.Parsed::class.java)
        assertThat(overLimit).isEqualTo(LocalRequest.TooLarge)
    }

    private fun statusOf(
        local: URL,
        path: String,
    ): Int =
        (URL(local.protocol, local.host, local.port, path).openConnection(Proxy.NO_PROXY) as HttpURLConnection).run {
            try {
                responseCode
            } finally {
                disconnect()
            }
        }

    /** Sends [request] byte for byte, which HttpURLConnection will not do for a malformed one. */
    private fun rawStatus(
        local: URL,
        request: String,
    ): Int =
        Socket(local.host, local.port).use { socket ->
            socket.soTimeout = 5_000
            // The server may answer and close before the whole request is written.
            runCatching {
                socket.getOutputStream().apply {
                    write(request.toByteArray(Charsets.US_ASCII))
                    flush()
                }
            }
            val statusLine =
                socket
                    .getInputStream()
                    .bufferedReader(Charsets.US_ASCII)
                    .readLine()
            statusLine.split(' ')[1].toInt()
        }
}
