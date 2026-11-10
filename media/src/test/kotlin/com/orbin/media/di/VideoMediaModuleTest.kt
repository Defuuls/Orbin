package com.orbin.media.di

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.google.common.truth.Truth.assertThat
import com.orbin.media.video.VideoPrefixCache
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

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
