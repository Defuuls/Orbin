package com.orbin.media.di

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.google.common.truth.Truth.assertThat
import com.orbin.media.video.VideoPrefixCache
import com.orbin.media.video.VideoServer
import com.orbin.network.NetworkConfig
import com.orbin.network.interceptor.HttpsOnlyInterceptor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress

/** Exercises the actual Hilt factory's scheme dispatch, without network or decoder mocks. */
@Suppress("UnsafeOptInUsageError")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoMediaModuleTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `downloaded local media streams and seeks without entering the HTTP client`() {
        val payload = "local media fixture".encodeToByteArray()
        val file = temporaryFolder.newFile("downloaded.mp4").apply { writeBytes(payload) }
        var httpCalls = 0
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor {
                    httpCalls++
                    error("A local file must not enter the network transport")
                }.build()
        val source =
            VideoMediaModule
                .providesVideoDataSourceFactory(
                    RuntimeEnvironment.getApplication(),
                    client,
                    VideoPrefixCache(),
                ).createDataSource()
        try {
            val length =
                source.open(
                    DataSpec
                        .Builder()
                        .setUri(Uri.fromFile(file))
                        .setPosition(3)
                        .setLength(5)
                        .build(),
                )
            assertThat(length).isEqualTo(5L)
            assertThat(source.readAll()).isEqualTo(payload.copyOfRange(3, 8))
            assertThat(httpCalls).isEqualTo(0)
        } finally {
            source.close()
        }
    }

    @Test
    fun `HTTPS media continues through the injected policy client`() {
        val payload = "secure media fixture".encodeToByteArray()
        val requested = mutableListOf<String>()
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    requested += chain.request().url.toString()
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(payload.toResponseBody())
                        .build()
                }.build()
        val source =
            VideoMediaModule
                .providesVideoDataSourceFactory(
                    RuntimeEnvironment.getApplication(),
                    client,
                    VideoPrefixCache(),
                ).createDataSource()
        try {
            source.open(DataSpec.Builder().setUri("https://media.example/clip.mp4").build())
            assertThat(source.readAll()).isEqualTo(payload)
            assertThat(requested).containsExactly("https://media.example/clip.mp4")
        } finally {
            source.close()
        }
    }

    @Test
    fun `loopback video streams and seeks without entering remote policy or DoH`() {
        val payload = "streamed video fixture"
        val client =
            OkHttpClient
                .Builder()
                .dns { error("The literal loopback address must not enter DoH") }
                .addInterceptor(HttpsOnlyInterceptor { NetworkConfig() })
                .build()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(payload))
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 3-7/${payload.length}")
                    .setBody(payload.substring(3, 8)),
            )
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            val uri = "http://127.0.0.1:${server.port}/clip"
            val source = videoSource(client)
            try {
                source.open(DataSpec.Builder().setUri(uri).build())
                assertThat(source.readAll()).isEqualTo(payload.encodeToByteArray())
                source.close()
                source.open(DataSpec.Builder().setUri(uri).setPosition(3).setLength(5).build())
                assertThat(source.readAll()).isEqualTo(payload.substring(3, 8).encodeToByteArray())
                assertThat(server.takeRequest().getHeader("Range")).isNull()
                assertThat(server.takeRequest().getHeader("Range")).isEqualTo("bytes=3-7")
            } finally {
                source.close()
            }
        }
    }

    @Test
    fun `completed short video still streams and seeks through its original loopback URI`() =
        runBlocking {
            val payload = "short video fixture"
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor(HttpsOnlyInterceptor { NetworkConfig() })
                    .addInterceptor { chain ->
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(payload.toResponseBody())
                            .build()
                    }.build()
            val cacheDir = temporaryFolder.newFolder()
            val server = VideoServer({ cacheDir }, client)
            val source = videoSource(client)
            val url = "https://media.example/short.mp4"
            try {
                val uri = server.source(url)
                withTimeout(5_000) {
                    while (server.cachedFile(url) == null) delay(10)
                }
                source.open(DataSpec.Builder().setUri(uri).build())
                assertThat(source.readAll()).isEqualTo(payload.encodeToByteArray())
                source.close()
                source.open(DataSpec.Builder().setUri(uri).setPosition(3).setLength(5).build())
                assertThat(source.readAll()).isEqualTo(payload.substring(3, 8).encodeToByteArray())
                source.close()
                server.clearCache()
                val error =
                    assertThrows(HttpDataSource.InvalidResponseCodeException::class.java) {
                        source.open(DataSpec.Builder().setUri(uri).build())
                    }
                assertThat(error.responseCode).isEqualTo(404)
            } finally {
                source.close()
                server.close()
            }
        }

    @Test
    fun `remote cleartext video still enters the HTTPS only policy`() {
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(HttpsOnlyInterceptor { NetworkConfig() })
                .build()
        val source = videoSource(client)
        try {
            val error =
                assertThrows(IOException::class.java) {
                    source.open(DataSpec.Builder().setUri("http://media.example/clip.mp4").build())
                }
            assertThat(error).hasCauseThat().hasMessageThat().contains("HTTPS-only is enabled")
        } finally {
            source.close()
        }
    }

    @Test
    fun `loopback redirects cannot escape into remote requests`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://media.example/clip.mp4"))
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            val source = videoSource(OkHttpClient())
            try {
                val error =
                    assertThrows(HttpDataSource.InvalidResponseCodeException::class.java) {
                        source.open(DataSpec.Builder().setUri("http://127.0.0.1:${server.port}/clip").build())
                    }
                assertThat(error.responseCode).isEqualTo(302)
                assertThat(server.requestCount).isEqualTo(1)
            } finally {
                source.close()
            }
        }
    }

    private fun videoSource(client: OkHttpClient): DataSource =
        VideoMediaModule
            .providesVideoDataSourceFactory(RuntimeEnvironment.getApplication(), client, VideoPrefixCache())
            .createDataSource()

    private fun DataSource.readAll(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8)
        while (true) {
            val count = read(buffer, 0, buffer.size)
            if (count == C.RESULT_END_OF_INPUT) return output.toByteArray()
            output.write(buffer, 0, count)
        }
    }
}
