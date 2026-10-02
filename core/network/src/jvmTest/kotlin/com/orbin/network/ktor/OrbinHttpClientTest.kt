package com.orbin.network.ktor

import com.google.common.truth.Truth.assertThat
import com.orbin.network.NetworkConfig
import com.orbin.network.policy.PowBlockTest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

class OrbinHttpClientTest {
    private val config = NetworkConfig(userAgent = "OrbinTest")

    @Test
    fun `applies the request policy headers`() {
        val seen = mutableListOf<HttpRequestData>()
        val client =
            orbinHttpClient(
                MockEngine {
                    seen += it
                    respond("{}")
                },
                { config },
            )

        runBlocking { client.get("https://i.example.org/g/1.webm") }

        val headers = seen.single().headers
        assertThat(headers["User-Agent"]).isEqualTo("OrbinTest")
        assertThat(headers["Referer"]).isEqualTo("https://i.example.org/")
        assertThat(headers["Accept"]).contains("video/*")
    }

    @Test
    fun `refuses cleartext under https only`() {
        var sent = 0
        val client =
            orbinHttpClient(
                MockEngine {
                    sent++
                    respond("{}")
                },
                { config },
            )

        assertThrows(CleartextBlockedException::class.java) {
            runBlocking { client.get("http://a.example.org/boards.json") }
        }
        assertThat(sent).isEqualTo(0)
    }

    @Test
    fun `clears the pow gate then the terms page and returns the real content`() {
        val site = GatedSite()
        val client = orbinHttpClient(MockEngine { site.handle(this, it) }, { config })

        val body = runBlocking { client.get("https://8chan.example/boards.js?json=1").bodyAsText() }

        assertThat(body).isEqualTo("""{"status":"ok"}""")
        assertThat(site.powSolved).isTrue()
        assertThat(site.termsAccepted).isTrue()
        // The confirmation is sent with the disclaimer as referer and the policy's user agent.
        assertThat(site.confirmHeaders?.get(HttpHeaders.Referrer)).endsWith("/.static/pages/disclaimer.html")
        assertThat(site.confirmHeaders?.get("User-Agent")).isEqualTo("OrbinTest")
    }

    @Test
    fun `leaves sites without a gate alone`() {
        var sent = 0
        val client =
            orbinHttpClient(
                MockEngine {
                    sent++
                    respond("<html>a page</html>", headers = HTML)
                },
                { config },
            )

        val body = runBlocking { client.get("https://a.example.org/").bodyAsText() }

        assertThat(body).isEqualTo("<html>a page</html>")
        assertThat(sent).isEqualTo(1)
    }

    /** A LynxChan site behind POWBlock, then a terms page; clearance is tracked server-side. */
    private class GatedSite {
        var powSolved = false
        var termsAccepted = false
        var confirmHeaders: io.ktor.http.Headers? = null

        fun handle(
            scope: MockRequestHandleScope,
            request: HttpRequestData,
        ): HttpResponseData =
            with(scope) {
                val path = request.url.encodedPath
                when {
                    request.url.parameters["powblock"] != null -> {
                        powSolved = true
                        respond("")
                    }
                    path.endsWith("/.static/pages/confirmed.html") -> {
                        termsAccepted = true
                        confirmHeaders = request.headers
                        respond("")
                    }
                    path.endsWith("/.static/pages/disclaimer.html") -> respond("I AGREE", headers = HTML)
                    !powSolved -> respond(PowBlockTest.interstitial(PowBlockTest.TOKEN, difficulty = 8), headers = HTML)
                    !termsAccepted ->
                        respond(
                            "",
                            HttpStatusCode.Found,
                            headersOf(HttpHeaders.Location, "/.static/pages/disclaimer.html"),
                        )
                    else -> respond("""{"status":"ok"}""")
                }
            }
    }

    private companion object {
        val HTML = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8")
    }
}
