package com.orbin.network.interceptor

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test
import java.security.MessageDigest

class PowBlockTest {
    @Test
    fun `parses challenge markers`() {
        val challenge = PowBlock.parse(interstitial(TOKEN, difficulty = 12))
        assertThat(challenge).isNotNull()
        assertThat(challenge!!.token).isEqualTo(TOKEN)
        assertThat(challenge.difficulty).isEqualTo(12)
        assertThat(challenge.algorithm).isEqualTo("SHA-256")
    }

    @Test
    fun `isChallenge only matches powblock interstitials`() {
        assertThat(PowBlock.isChallenge(interstitial(TOKEN, difficulty = 8))).isTrue()
        assertThat(PowBlock.isChallenge("""{"status":"ok"}""")).isFalse()
    }

    @Test
    fun `solved nonce satisfies the difficulty`() {
        val difficulty = 10
        val challenge = PowBlock.parse(interstitial(TOKEN, difficulty))!!
        val nonce = PowBlock.solve(challenge)
        assertThat(nonce).isNotNull()

        val hash = MessageDigest.getInstance("SHA-256").digest((TOKEN + nonce).toByteArray())
        assertThat(leadingZeroBits(hash)).isAtLeast(difficulty)
    }

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
            val body = response.use { it.body!!.string() }

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

        fun leadingZeroBits(hash: ByteArray): Int {
            var bits = 0
            for (byte in hash) {
                val value = byte.toInt() and 0xFF
                if (value == 0) {
                    bits += 8
                    continue
                }
                var mask = 0x80
                while (mask != 0 && (value and mask) == 0) {
                    bits++
                    mask = mask shr 1
                }
                break
            }
            return bits
        }
    }
}
