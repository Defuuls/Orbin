package com.orbin.network.interceptor

import com.google.common.truth.Truth.assertThat
import com.orbin.network.MediaResponseCache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Test
import java.util.concurrent.Executors

class MediaResponseCacheInterceptorTest {
    @Test
    fun `reopening gif uses one request and clearing cache reaches the origin again`() {
        MockWebServer().use { server ->
            server.enqueue(gif("GIF89a-first"))
            server.enqueue(MockResponse().setResponseCode(429))
            server.start()
            val cache = MemoryStore()
            val client = client(cache)
            val request = Request.Builder().url(server.url("/original.gif")).build()
            repeat(4) {
                client.newCall(request).execute().use { assertThat(it.body.string()).isEqualTo("GIF89a-first") }
            }
            assertThat(server.requestCount).isEqualTo(1)
            cache.clear()
            client.newCall(request).execute().use { assertThat(it.code).isEqualTo(429) }
            assertThat(server.requestCount).isEqualTo(2)
        }
    }

    @Test
    fun `concurrent display and preload share the completed gif`() {
        MockWebServer().use { server ->
            server.enqueue(gif("GIF89a-shared"))
            server.start()
            val client = client(MemoryStore())
            val request = Request.Builder().url(server.url("/same.gif")).build()
            val pool = Executors.newFixedThreadPool(4)
            try {
                val loads =
                    (1..4).map {
                        pool.submit<String> {
                            client
                                .newCall(
                                    request,
                                ).execute()
                                .use { it.body.string() }
                        }
                    }
                assertThat(loads.map { it.get() }).containsExactlyElementsIn(List(4) { "GIF89a-shared" })
                assertThat(server.requestCount).isEqualTo(1)
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun `no store error and malformed partial responses are never cached`() {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(gif("private").setHeader("Cache-Control", "no-store")) }
            repeat(2) { server.enqueue(MockResponse().setResponseCode(429)) }
            repeat(2) { server.enqueue(gif("part").setResponseCode(206)) }
            server.start()
            val client = client(MemoryStore())
            repeat(6) {
                client
                    .newCall(
                        Request.Builder().url(server.url("/no-cache.gif")).build(),
                    ).execute()
                    .use { it.body.string() }
            }
            assertThat(server.requestCount).isEqualTo(6)
        }
    }

    @Test
    fun `an unread player response cannot indefinitely block another player`() {
        MockWebServer().use { server ->
            server.enqueue(gif("first"))
            server.enqueue(gif("second"))
            server.start()
            val client = client(MemoryStore())
            val request = Request.Builder().url(server.url("/paused.gif")).build()
            client.newCall(request).execute().use {
                client.newCall(request).execute().use { second ->
                    assertThat(second.body.string()).isEqualTo("second")
                }
            }
            assertThat(server.requestCount).isEqualTo(2)
        }
    }

    @Test
    fun `expired media returns to the origin`() {
        MockWebServer().use { server ->
            server.enqueue(gif("old").setHeader("Cache-Control", "max-age=1"))
            server.enqueue(gif("new"))
            server.start()
            var now = 1000L
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor(
                        MediaResponseCacheInterceptor(MemoryStore(), now = { now }),
                    ).build()
            val request = Request.Builder().url(server.url("/expired.gif")).build()
            client.newCall(request).execute().use { assertThat(it.body.string()).isEqualTo("old") }
            now += 2000L
            client.newCall(request).execute().use { assertThat(it.body.string()).isEqualTo("new") }
            assertThat(server.requestCount).isEqualTo(2)
        }
    }

    @Test
    fun `video gif loops use cached bytes even while CDN backoff is active`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("video-gif").setHeader("Content-Type", "video/webm"))
            server.start()
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor(MediaResponseCacheInterceptor(MemoryStore()))
                    .addInterceptor(VideoRetryAfterInterceptor())
                    .build()
            val request = Request.Builder().url(server.url("/animated.webm")).build()
            RetryAfterTracker.clear()
            try {
                client.newCall(request).execute().use { assertThat(it.body.string()).isEqualTo("video-gif") }
                RetryAfterTracker.record(request.url.host, request.url.toString(), 60)
                repeat(3) {
                    client.newCall(request).execute().use {
                        assertThat(it.code).isEqualTo(200)
                        assertThat(it.body.string()).isEqualTo("video-gif")
                    }
                }
                assertThat(server.requestCount).isEqualTo(1)
            } finally {
                RetryAfterTracker.clear()
            }
        }
    }

    @Test
    fun `video byte ranges retain status boundaries and distinct bytes`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setBody("abcd")
                    .setHeader("Content-Type", "video/webm")
                    .setHeader("Content-Range", "bytes 0-3/8"),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setBody("efgh")
                    .setHeader("Content-Type", "video/webm")
                    .setHeader("Content-Range", "bytes 4-7/8"),
            )
            server.start()
            val client = client(MemoryStore())
            for ((range, expected) in listOf("0-3" to "abcd", "4-7" to "efgh", "0-3" to "abcd", "4-7" to "efgh")) {
                val request =
                    Request
                        .Builder()
                        .url(server.url("/seek.webm"))
                        .header("Range", "bytes=$range")
                        .build()
                client.newCall(request).execute().use {
                    assertThat(it.code).isEqualTo(206)
                    assertThat(it.header("Content-Range")).isEqualTo("bytes $range/8")
                    assertThat(it.body.string()).isEqualTo(expected)
                }
            }
            assertThat(server.requestCount).isEqualTo(2)
        }
    }

    private fun client(store: MediaResponseCache) =
        OkHttpClient
            .Builder()
            .addInterceptor(MediaResponseCacheInterceptor(store))
            .build()

    private fun gif(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "image/gif")

    private class MemoryStore : MediaResponseCache {
        private data class Stored(
            val type: String,
            val bytes: ByteArray,
            val expires: Long,
            val status: Int,
            val range: String?,
        )

        private val entries = mutableMapOf<String, Stored>()

        @Synchronized
        override fun open(
            key: String,
            nowMillis: Long,
        ): MediaResponseCache.Entry? {
            val row = entries[key]?.takeIf { it.expires > nowMillis } ?: return null
            return MediaResponseCache.Entry(
                row.type,
                row.bytes.size.toLong(),
                Buffer().write(row.bytes),
                statusCode = row.status,
                contentRange = row.range,
            )
        }

        override fun start(
            key: String,
            contentType: String,
            expiresAtMillis: Long,
            statusCode: Int,
            contentRange: String?,
        ): MediaResponseCache.Write =
            object : MediaResponseCache.Write {
                val buffer = Buffer()

                override fun append(bytes: ByteArray) {
                    buffer.write(bytes)
                }

                override fun commit() {
                    synchronized(this@MemoryStore) {
                        entries[key] =
                            Stored(contentType, buffer.readByteArray(), expiresAtMillis, statusCode, contentRange)
                    }
                }

                override fun abort() {
                    buffer.clear()
                }
            }

        @Synchronized
        override fun clear() {
            entries.clear()
        }

        override fun usageBytes(): Long = entries.values.sumOf { it.bytes.size.toLong() }
    }
}
