package com.orbin.media.video

import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Real loopback TLS requests exercise the preloader and the same Media3 transport factory. */
@Suppress("UnsafeOptInUsageError")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoPreloaderTest {
    private val server = MockWebServer()
    private val cache = VideoPrefixCache()
    private lateinit var client: OkHttpClient

    @Before
    fun startTlsServer() {
        val certificate =
            HeldCertificate
                .Builder()
                .commonName(
                    "localhost",
                ).addSubjectAlternativeName("localhost")
                .build()
        val serverTrust = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        server.useHttps(serverTrust.sslSocketFactory(), false)
        server.start()
        val clientTrust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        client =
            OkHttpClient.Builder().sslSocketFactory(clientTrust.sslSocketFactory(), clientTrust.trustManager).build()
    }

    @After
    fun stopServer() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        server.shutdown()
    }

    @Test
    fun `bounded prefix joins a real TLS tail request through Media3`() =
        runBlocking {
            val payload = ByteArray(VideoPrefixCache.MAX_PREFIX_BYTES + 8192) { (it % 127).toByte() }
            val prefixSize = VideoPrefixCache.MAX_PREFIX_BYTES
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Type", "video/webm")
                    .setHeader("Content-Range", "bytes 0-${prefixSize - 1}/${payload.size}")
                    .setBody(Buffer().write(payload, 0, prefixSize)),
            )
            val url = server.url("/clip.webm").toString()
            val job = checkNotNull(VideoPreloader(client, cache).prefetch(url, this))
            withTimeout(10_000) { job.join() }
            assertThat(
                server.takeRequest(1, TimeUnit.SECONDS)?.getHeader("Range"),
            ).isEqualTo("bytes=0-${prefixSize - 1}")
            assertThat(cache.get(url)?.bytes?.size).isEqualTo(prefixSize)
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Type", "video/webm")
                    .setHeader("Content-Range", "bytes $prefixSize-${payload.lastIndex}/${payload.size}")
                    .setBody(Buffer().write(payload, prefixSize, payload.size - prefixSize)),
            )
            val source = VideoPrefixDataSource(OkHttpDataSource.Factory(client).createDataSource(), cache)
            assertThat(source.open(DataSpec.Builder().setUri(url).build())).isEqualTo(payload.size.toLong())
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            try {
                while (true) {
                    val count = source.read(buffer, 0, buffer.size)
                    if (count == C.RESULT_END_OF_INPUT) break
                    result.write(buffer, 0, count)
                }
            } finally {
                source.close()
            }
            assertThat(result.toByteArray()).isEqualTo(payload)
            assertThat(server.takeRequest(1, TimeUnit.SECONDS)?.getHeader("Range")).isEqualTo("bytes=$prefixSize-")
        }

    @Test
    fun `cancelling an obsolete prefetch cancels the live TLS call without caching it`() =
        runBlocking {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val url = server.url("/slow.webm").toString()
            val job = checkNotNull(VideoPreloader(client, cache).prefetch(url, this))
            // Let the launched child enqueue its request before blocking this runBlocking thread.
            yield()
            assertThat(server.takeRequest(5, TimeUnit.SECONDS)).isNotNull()
            withTimeout(2_000) {
                job.cancelAndJoin()
                while (client.dispatcher.runningCallsCount() > 0) delay(10)
            }
            assertThat(cache.get(url)).isNull()
        }

    @Test
    fun `a nonzero partial response cannot masquerade as the clip prefix`() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Type", "video/webm")
                    .setHeader("Content-Range", "bytes 5-8/12")
                    .setBody("tail"),
            )
            val url = server.url("/wrong-range.webm").toString()
            val job = checkNotNull(VideoPreloader(client, cache).prefetch(url, this))
            withTimeout(10_000) { job.join() }
            assertThat(cache.get(url)).isNull()
        }

    @Test
    fun `an origin ignoring Range on a large clip leaves no unusable prefix`() =
        runBlocking {
            val payload = ByteArray(VideoPrefixCache.MAX_PREFIX_BYTES + 8192)
            server.enqueue(MockResponse().setBody(Buffer().write(payload)).setHeader("Content-Type", "video/webm"))
            val url = server.url("/range-ignored.webm").toString()
            val job = checkNotNull(VideoPreloader(client, cache).prefetch(url, this))
            withTimeout(10_000) { job.join() }
            assertThat(cache.get(url)).isNull()
        }

    @Test
    fun `a complete small clip from a Range ignoring origin can still be reused`() =
        runBlocking {
            val payload = ByteArray(32) { it.toByte() }
            server.enqueue(MockResponse().setBody(Buffer().write(payload)).setHeader("Content-Type", "video/webm"))
            val url = server.url("/complete.webm").toString()
            val job = checkNotNull(VideoPreloader(client, cache).prefetch(url, this))
            withTimeout(10_000) { job.join() }
            assertThat(cache.get(url)?.bytes).isEqualTo(payload)
            assertThat(cache.get(url)?.totalLength).isEqualTo(payload.size.toLong())
        }

    @Test
    fun `cleartext media is never prefetched`() =
        runBlocking {
            assertThat(VideoPreloader(client, cache).prefetch("http://localhost/clip.webm", this)).isNull()
            assertThat(server.requestCount).isEqualTo(0)
        }
}
