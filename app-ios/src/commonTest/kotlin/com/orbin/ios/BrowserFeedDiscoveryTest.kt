package com.orbin.ios

import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.graph.orbinProviders
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Directory retry and cancellation use the real provider adapters, independent of catalog paging. */
class BrowserFeedDiscoveryTest {
    @Test
    fun retryRefreshesAnInitiallyUnavailableDirectoryAndStartsDiscovery() =
        runTest {
            var available = false
            var directoryRequests = 0
            val engine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    if (path == "/boards.json") directoryRequests++
                    val body =
                        when {
                            available && path == "/boards.json" -> FOURCHAN_BOARDS
                            available && path == "/g/catalog.json" -> FOURCHAN_CATALOG
                            else -> null
                        }
                    respond(
                        body.orEmpty(),
                        if (body == null) HttpStatusCode.NotFound else HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val browser =
                Browser(
                    orbinProviders(iosHttpClient(engine)),
                    FakeBookmarks(),
                    FakeHistory(),
                    followedPreferences(),
                    FakeSettings(),
                    backgroundScope,
                )
            browser.firstRun.state.first { it.initialized }
            assertIs<Load.Failed>(browser.feed.first { it is Load.Failed })
            available = true
            browser.retry()
            val feed =
                assertIs<Load.Ready<List<FeedThread>>>(browser.feed.first { it is Load.Ready && it.value.isNotEmpty() })
            assertTrue(feed.value.any { it.provider.value == "fourchan" })
            assertTrue(directoryRequests >= 2)
        }

    @Test
    fun selectingTheUnavailableSourceShowsRetryAndRetryRefreshesThatDirectory() =
        runTest {
            var secondAvailable = false
            var secondDirectoryRequests = 0
            val engine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    if (path == "/boards.js") secondDirectoryRequests++
                    val body =
                        when {
                            path == "/boards.json" -> FOURCHAN_BOARDS
                            path == "/g/catalog.json" -> FOURCHAN_CATALOG
                            secondAvailable && path == "/boards.js" -> LYNXCHAN_BOARDS
                            secondAvailable && path == "/b/catalog.json" -> """[{"threadId":5,"subject":"Hi"}]"""
                            else -> null
                        }
                    respond(
                        body.orEmpty(),
                        if (body == null) HttpStatusCode.NotFound else HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val browser =
                Browser(
                    orbinProviders(iosHttpClient(engine)),
                    FakeBookmarks(),
                    FakeHistory(),
                    followedPreferences(),
                    FakeSettings(),
                    backgroundScope,
                )
            browser.firstRun.state.first { it.initialized }
            browser.unreachableSites.first { it.any { name -> name.startsWith("BBWChan") } }
            browser.selectFeedSite(ProviderId("bbwchan"))
            assertIs<Load.Failed>(browser.feed.first { it is Load.Failed })
            secondAvailable = true
            browser.retry()
            val feed =
                assertIs<Load.Ready<List<FeedThread>>>(browser.feed.first { it is Load.Ready && it.value.isNotEmpty() })
            assertTrue(feed.value.all { it.provider.value == "bbwchan" })
            assertTrue(secondDirectoryRequests >= 2)
        }

    @Test
    fun aLateInitialDirectoryFailureCannotReplaceASuccessfulRetry() =
        runTest {
            val releaseOld = CompletableDeferred<Unit>()
            val oldStarted = CompletableDeferred<Unit>()
            var first = true
            val engine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    val body =
                        when {
                            path == "/boards.json" && first -> {
                                first = false
                                oldStarted.complete(Unit)
                                withContext(NonCancellable) { releaseOld.await() }
                                null
                            }
                            path == "/boards.json" -> FOURCHAN_BOARDS
                            path == "/g/catalog.json" -> FOURCHAN_CATALOG
                            path == "/boards.js" -> LYNXCHAN_BOARDS
                            else -> null
                        }
                    respond(
                        body.orEmpty(),
                        if (body == null) HttpStatusCode.NotFound else HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val browser =
                Browser(
                    orbinProviders(iosHttpClient(engine)),
                    FakeBookmarks(),
                    FakeHistory(),
                    followedPreferences(),
                    FakeSettings(),
                    backgroundScope,
                )
            oldStarted.await()
            browser.firstRun.state.first { it.initialized }
            browser.retry()
            browser.feed.first { it is Load.Ready && it.value.isNotEmpty() }
            releaseOld.complete(Unit)
            browser.boards.first { it is Load.Ready && it.value.any { board -> board.provider.value == "fourchan" } }
            assertTrue(browser.unreachableSites.value.none { it.startsWith("4chan") })
        }

    private fun followedPreferences() =
        FakeBoardPreferences().apply {
            subscribed.value = mapOf(ProviderId("fourchan") to setOf(BoardId("g")))
        }

    companion object {
        private const val FOURCHAN_BOARDS = """{"boards":[{"board":"g","title":"Technology","ws_board":1}]}"""
        private const val FOURCHAN_CATALOG =
            """[{"page":1,"threads":[{"no":7,"sub":"Hi","time":1,"tim":7,"ext":".mp4"}]}]"""
        private const val LYNXCHAN_BOARDS =
            """{"status":"ok","data":{"boards":[{"boardUri":"b","boardName":"General","sfw":true}]}}"""
    }
}
