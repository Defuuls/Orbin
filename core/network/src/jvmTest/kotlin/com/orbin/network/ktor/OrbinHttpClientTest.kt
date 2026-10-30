package com.orbin.network.ktor

import com.google.common.truth.Truth.assertThat
import com.orbin.network.NetworkConfig
import com.orbin.network.policy.PowBlock
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
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
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
    fun `requests that hit the gate together mine it once`() {
        var cleared = false
        var submissions = 0
        val client =
            orbinHttpClient(
                MockEngine { request ->
                    when {
                        request.url.parameters["powblock"] != null -> {
                            submissions++
                            cleared = true
                            respond("")
                        }
                        !cleared ->
                            respond(
                                PowBlockTest.interstitial(PowBlockTest.TOKEN, difficulty = 8),
                                headers = HTML,
                            )
                        else -> respond("ok")
                    }
                },
                { config },
            )

        // All four are challenged before any clears the gate: each sends before the first mines.
        val bodies =
            runBlocking {
                (1..4)
                    .map { index -> async { client.get("https://8chan.example/thumb/$index.jpg").bodyAsText() } }
                    .awaitAll()
            }

        assertThat(bodies).containsExactly("ok", "ok", "ok", "ok")
        assertThat(submissions).isEqualTo(1)
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

    @Test
    fun `rejects oversized html with missing or misleading length`() {
        for (declared in listOf<String?>(null, "1", (PowBlock.MAX_INTERSTITIAL_BYTES + 1).toString())) {
            val responseHeaders =
                io.ktor.http.Headers.build {
                    append(HttpHeaders.ContentType, "text/html")
                    declared?.let { append(HttpHeaders.ContentLength, it) }
                }
            val client =
                orbinHttpClient(
                    MockEngine {
                        respond("x".repeat(PowBlock.MAX_INTERSTITIAL_BYTES.toInt() + 1), headers = responseHeaders)
                    },
                    { config },
                )
            try {
                assertThrows(IOException::class.java) {
                    runBlocking { client.get("https://a.example.org/").bodyAsText() }
                }
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `clearance response bodies are discarded without saving`() {
        var cleared = false
        val client =
            orbinHttpClient(
                MockEngine { request ->
                    when {
                        request.url.parameters["powblock"] != null -> {
                            cleared = true
                            respond(
                                "x".repeat(PowBlock.MAX_INTERSTITIAL_BYTES.toInt() * 2),
                                headers = HTML,
                            )
                        }
                        !cleared ->
                            respond(
                                PowBlockTest.interstitial(PowBlockTest.TOKEN, difficulty = 8),
                                headers = HTML,
                            )
                        else -> respond("ok")
                    }
                },
                { config },
            )
        try {
            assertThat(runBlocking { client.get("https://a.example.org/").bodyAsText() }).isEqualTo("ok")
        } finally {
            client.close()
        }
    }

    @Test
    fun `html limit stops the producer before default response buffering`() =
        runBlocking {
            val channel = ByteChannel(autoFlush = true)
            var produced = 0
            val chunks = 512
            val producer =
                launch {
                    runCatching {
                        repeat(chunks) {
                            channel.writeFully(ByteArray(8192))
                            produced++
                        }
                        channel.close()
                    }
                }
            val client = orbinHttpClient(MockEngine { respond(channel, headers = HTML) }, { config })
            try {
                val failure = runCatching { client.get("https://a.example.org/") }.exceptionOrNull()
                assertThat(failure).isInstanceOf(IOException::class.java)
                assertThat(produced).isLessThan(chunks)
            } finally {
                producer.cancelAndJoin()
                client.close()
            }
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
