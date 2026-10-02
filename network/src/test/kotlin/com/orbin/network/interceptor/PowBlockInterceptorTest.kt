package com.orbin.network.interceptor

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test

/** OkHttp's adapter for the shared gate; the gate's own logic is tested in :core:network. */
class PowBlockInterceptorTest {
    @Test
    fun `interceptor clears pow gate then tos gate and returns real content`() {
        MockWebServer().use { server ->
            server.dispatcher = GateDispatcher()
            server.start()

            val client =
                OkHttpClient
                    .Builder()
                    .cookieJar(InMemoryCookieJar())
                    .addInterceptor(PowBlockInterceptor())
                    .build()

            val response = client.newCall(Request.Builder().url(server.url("/boards.js?json=1")).build()).execute()
            val body = response.use { it.body.string() }

            assertThat(body).isEqualTo("""{"status":"ok"}""")
            // First hit (challenge), the pow submission, the tos-gated retry that 302s to the
            // disclaimer, the confirmed.html acceptance, and the final cleared request.
            assertThat(server.requestCount).isAtLeast(FULLY_CLEARED_REQUEST_COUNT)
        }
    }

    /** Serves the POWBlock interstitial, then the ToS redirect, then real content once cleared. */
    private class GateDispatcher : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path.orEmpty()
            val cookies = request.getHeader("Cookie").orEmpty()
            return when {
                path.contains("powblock=") ->
                    MockResponse().setResponseCode(HTTP_OK).addHeader("Set-Cookie", "POW_TOKEN=granted; Path=/")
                path.endsWith("/.static/pages/confirmed.html") ->
                    MockResponse().setResponseCode(HTTP_OK).addHeader("Set-Cookie", "TOS=1; Path=/")
                path.endsWith("/.static/pages/disclaimer.html") ->
                    MockResponse().setResponseCode(HTTP_OK).setHeader("Content-Type", "text/html").setBody("I AGREE")
                !cookies.contains("POW_TOKEN") ->
                    MockResponse()
                        .setResponseCode(HTTP_OK)
                        .setHeader("Content-Type", "text/html")
                        .setBody(interstitial(TOKEN, difficulty = 8))
                !cookies.contains("TOS=1") ->
                    MockResponse().setResponseCode(HTTP_FOUND).setHeader("Location", "/.static/pages/disclaimer.html")
                else ->
                    MockResponse().setResponseCode(HTTP_OK).setBody("""{"status":"ok"}""")
            }
        }
    }

    private companion object {
        const val TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcd"
        const val HTTP_OK = 200
        const val HTTP_FOUND = 302
        const val FULLY_CLEARED_REQUEST_COUNT = 5

        fun interstitial(
            token: String,
            difficulty: Int,
        ): String =
            """
            <html><head><title>POWBlock Check…</title></head><body>
            <div class=footer>POWBlock v1.8x Enterprise</div>
            <pre id=c style=display:none>$token</pre>
            <pre id=d style=display:none>$difficulty</pre>
            <pre id=h style=display:none>256</pre>
            </body></html>
            """.trimIndent()
    }
}
