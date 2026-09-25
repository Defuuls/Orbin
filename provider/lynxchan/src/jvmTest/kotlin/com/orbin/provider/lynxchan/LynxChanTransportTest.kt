package com.orbin.provider.lynxchan

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.ThreadId
import com.orbin.provider.api.ProviderException
import com.orbin.provider.lynxchan.api.KtorLynxChanApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.IOException

/** The Ktor transport: request URLs, decoding, and HTTP/IO failures mapped to [ProviderException]. */
class LynxChanTransportTest {
    private val requested = mutableListOf<String>()

    private fun provider(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): LynxChanProvider {
        val engine =
            MockEngine { request ->
                requested += request.url.toString()
                handler(request)
            }
        val json = Json { ignoreUnknownKeys = true }
        val site = LynxChanSite.BbwChan
        return LynxChanProvider(
            site,
            KtorLynxChanApi(HttpClient(engine), site.apiBaseUrl, json),
            Dispatchers.Unconfined,
        )
    }

    private fun MockRequestHandleScope.json(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun `requests each endpoint under the site url and decodes it`() =
        runTest {
            val provider =
                provider { request ->
                    when (request.url.encodedPath) {
                        "/boards.js" ->
                            json(
                                """{"status":"ok","data":{"boards":[{"boardUri":"b","boardName":"Random"}]}}""",
                            )
                        "/b/catalog.json" -> json("""[{"threadId":5,"subject":"Hi"}]""")
                        "/b/res/5.json" -> json("""{"threadId":5,"subject":"Hi"}""")
                        else -> respond("", HttpStatusCode.NotFound)
                    }
                }

            assertThat(provider.getBoards().map { it.id.value }).containsExactly("b")
            assertThat(
                provider.getCatalog(CatalogRequest(provider.metadata.id, BoardId("b"))).map { it.key.thread.value },
            ).containsExactly(5L)
            assertThat(
                provider
                    .getThread(BoardId("b"), ThreadId(5))
                    .key.thread.value,
            ).isEqualTo(5L)
            assertThat(requested)
                .containsExactly(
                    "https://bbw-chan.link/boards.js?json=1",
                    "https://bbw-chan.link/b/catalog.json",
                    "https://bbw-chan.link/b/res/5.json",
                ).inOrder()
        }

    @Test
    fun `a 404 becomes NotFound`() =
        runTest {
            val error = runCatching { provider { respond("", HttpStatusCode.NotFound) }.getBoards() }.exceptionOrNull()
            assertThat(error).isInstanceOf(ProviderException.NotFound::class.java)
        }

    @Test
    fun `a 429 carries Retry-After seconds`() =
        runTest {
            val error =
                runCatching {
                    provider {
                        respond(
                            "",
                            HttpStatusCode.TooManyRequests,
                            headersOf(HttpHeaders.RetryAfter, "30"),
                        )
                    }.getBoards()
                }.exceptionOrNull()
            assertThat((error as ProviderException.RateLimited).retryAfterSeconds).isEqualTo(30L)
        }

    @Test
    fun `other statuses become Http with the code`() =
        runTest {
            val error =
                runCatching {
                    provider {
                        respond(
                            "",
                            HttpStatusCode.BadGateway,
                        )
                    }.getBoards()
                }.exceptionOrNull()
            assertThat((error as ProviderException.Http).code).isEqualTo(502)
        }

    @Test
    fun `malformed JSON becomes Parse`() =
        runTest {
            val error = runCatching { provider { json("{") }.getBoards() }.exceptionOrNull()
            assertThat(error).isInstanceOf(ProviderException.Parse::class.java)
        }

    @Test
    fun `a transport failure becomes Network`() =
        runTest {
            val error = runCatching { provider { throw IOException("connection reset") }.getBoards() }.exceptionOrNull()
            assertThat(error).isInstanceOf(ProviderException.Network::class.java)
        }
}
