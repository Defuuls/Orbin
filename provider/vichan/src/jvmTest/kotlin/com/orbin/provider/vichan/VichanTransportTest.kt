package com.orbin.provider.vichan

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.ThreadId
import com.orbin.provider.api.ProviderException
import com.orbin.provider.vichan.api.KtorVichanApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.toHttpDate
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import java.io.IOException

/** The Ktor transport: request URLs, decoding, and HTTP/IO failures mapped to [ProviderException]. */
class VichanTransportTest {
    private val requested = mutableListOf<String>()

    private fun provider(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): VichanProvider {
        val engine =
            MockEngine { request ->
                requested += request.url.toString()
                handler(request)
            }
        val json = Json { ignoreUnknownKeys = true }
        val site = VichanSite.Example
        return VichanProvider(site, KtorVichanApi(HttpClient(engine), site.apiBaseUrl, json), Dispatchers.Unconfined)
    }

    private fun MockRequestHandleScope.json(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun `requests each endpoint under the api base url and decodes it`() =
        runTest {
            val provider =
                provider { request ->
                    when (request.url.encodedPath) {
                        "/boards.json" -> json("""{"boards":[{"board":"g","title":"Technology"}]}""")
                        "/g/catalog.json" -> json("""[{"page":1,"threads":[{"no":7,"sub":"Hi","time":1}]}]""")
                        "/g/thread/7.json" -> json("""{"posts":[{"no":7,"sub":"Hi","time":1}]}""")
                        else -> respond("", HttpStatusCode.NotFound)
                    }
                }

            assertThat(provider.getBoards().map { it.id.value }).containsExactly("g")
            assertThat(
                provider.getCatalog(CatalogRequest(provider.metadata.id, BoardId("g"))).map { it.key.thread.value },
            ).containsExactly(7L)
            assertThat(
                provider
                    .getThread(BoardId("g"), ThreadId(7))
                    .key.thread.value,
            ).isEqualTo(7L)
            assertThat(requested)
                .containsExactly(
                    "https://a.4cdn.org/boards.json",
                    "https://a.4cdn.org/g/catalog.json",
                    "https://a.4cdn.org/g/thread/7.json",
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
                            headersOf(HttpHeaders.RetryAfter, "120"),
                        )
                    }.getBoards()
                }.exceptionOrNull()
            assertThat((error as ProviderException.RateLimited).retryAfterSeconds).isEqualTo(120L)
        }

    @Test
    fun `a 429 with an HTTP-date Retry-After is converted to seconds from now`() =
        runTest {
            val retryAt = GMTDate(System.currentTimeMillis() + 120_000).toHttpDate()
            val error =
                runCatching {
                    provider {
                        respond(
                            "",
                            HttpStatusCode.TooManyRequests,
                            headersOf(HttpHeaders.RetryAfter, retryAt),
                        )
                    }.getBoards()
                }.exceptionOrNull()
            assertThat((error as ProviderException.RateLimited).retryAfterSeconds).isIn(100L..120L)
        }

    @Test
    fun `other statuses become Http with the code`() =
        runTest {
            val error =
                runCatching {
                    provider {
                        respond(
                            "",
                            HttpStatusCode.InternalServerError,
                        )
                    }.getBoards()
                }.exceptionOrNull()
            assertThat((error as ProviderException.Http).code).isEqualTo(500)
        }

    @Test
    fun `malformed JSON becomes Parse`() =
        runTest {
            val error = runCatching { provider { json("not json") }.getBoards() }.exceptionOrNull()
            assertThat(error).isInstanceOf(ProviderException.Parse::class.java)
        }

    @Test
    fun `a transport failure becomes Network`() =
        runTest {
            val error = runCatching { provider { throw IOException("connection reset") }.getBoards() }.exceptionOrNull()
            assertThat(error).isInstanceOf(ProviderException.Network::class.java)
        }
}
