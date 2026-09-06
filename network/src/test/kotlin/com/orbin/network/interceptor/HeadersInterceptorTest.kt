package com.orbin.network.interceptor

import com.google.common.truth.Truth.assertThat
import com.orbin.network.NetworkConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

class HeadersInterceptorTest {
    @Test
    fun `allows short lived cache on idempotent catalog and thread gets`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse())
            server.start()

            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor(HeadersInterceptor { NetworkConfig(userAgent = "OrbinTest") })
                    .build()

            client.newCall(Request.Builder().url(server.url("/b/catalog.json")).build()).execute().close()

            val request = server.takeRequest()
            assertThat(request.getHeader("User-Agent")).isEqualTo("OrbinTest")
            assertThat(request.getHeader("Cache-Control")).isEqualTo("max-age=60")
            assertThat(request.getHeader("Pragma")).isNull()
        }
    }

    @Test
    fun `keeps no-store on mutating api requests`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse())
            server.start()

            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor(HeadersInterceptor { NetworkConfig(userAgent = "OrbinTest") })
                    .build()

            client
                .newCall(
                    Request
                        .Builder()
                        .url(server.url("/post"))
                        .post(ByteArray(0).toRequestBody(null))
                        .build(),
                ).execute()
                .close()

            val request = server.takeRequest()
            assertThat(request.getHeader("User-Agent")).isEqualTo("OrbinTest")
            assertThat(request.getHeader("Cache-Control")).isEqualTo("no-store")
            assertThat(request.getHeader("Pragma")).isEqualTo("no-cache")
        }
    }

    @Test
    fun `allows static media responses to use normal http caching`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse())
            server.start()

            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor(HeadersInterceptor { NetworkConfig(userAgent = "OrbinTest") })
                    .build()

            client.newCall(Request.Builder().url(server.url("/g/1690000000000.jpg")).build()).execute().close()

            val request = server.takeRequest()
            assertThat(request.getHeader("User-Agent")).isEqualTo("OrbinTest")
            assertThat(request.getHeader("Accept")).contains("image/*")
            assertThat(request.getHeader("Referer")).isEqualTo(server.url("/").toString())
            assertThat(request.getHeader("Cache-Control")).isNull()
            assertThat(request.getHeader("Pragma")).isNull()
        }
    }
}
