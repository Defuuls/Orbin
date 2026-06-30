package com.orbin.network.interceptor

import com.google.common.truth.Truth.assertThat
import com.orbin.network.NetworkConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

class HeadersInterceptorTest {
    @Test
    fun `adds privacy preserving cache headers`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse())
            server.start()

            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor(HeadersInterceptor { NetworkConfig(userAgent = "OrbinTest") })
                    .build()

            client.newCall(Request.Builder().url(server.url("/thread")).build()).execute().close()

            val request = server.takeRequest()
            assertThat(request.getHeader("User-Agent")).isEqualTo("OrbinTest")
            assertThat(request.getHeader("Cache-Control")).isEqualTo("no-store")
            assertThat(request.getHeader("Pragma")).isEqualTo("no-cache")
        }
    }
}
