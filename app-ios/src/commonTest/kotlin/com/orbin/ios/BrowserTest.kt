package com.orbin.ios

import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The iOS browser end to end over the real providers, with the network replaced by a script. */
class BrowserTest {
    private val requested = mutableListOf<String>()
    private val userAgents = mutableSetOf<String?>()
    private val bookmarks = FakeBookmarks()
    private val history = FakeHistory()

    private fun browser(
        scope: CoroutineScope,
        routes: Map<String, String?>,
    ): Browser {
        val engine =
            MockEngine { request ->
                requested += request.url.toString()
                userAgents += request.headers[HttpHeaders.UserAgent]
                reply(request, routes)
            }
        return Browser(orbinProviders(orbinHttpClient(engine)), bookmarks, history, scope, now = { NOW })
    }

    private fun MockRequestHandleScope.reply(
        request: HttpRequestData,
        routes: Map<String, String?>,
    ): HttpResponseData {
        val body = routes["${request.url.host}${request.url.encodedPath}"]
        return if (body == null) {
            respond("", HttpStatusCode.NotFound)
        } else {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    }

    private suspend fun <T> StateFlow<Load<T>>.settled(): Load<T> = first { it !is Load.Loading }

    @Test
    fun boardsFromEverySiteAreListedAndAThreadOpensThroughTheCatalog() =
        runTest {
            val browser = browser(backgroundScope, BOTH_SITES)

            val boards = assertIs<Load.Ready<List<SiteBoard>>>(browser.boards.settled()).value
            assertEquals(listOf("g", "b"), boards.map { it.board.id.value })
            assertEquals(2, boards.map { it.provider }.toSet().size)

            val technology = boards.first { it.board.id.value == "g" }
            browser.openBoard(technology)
            val catalog = assertIs<Load.Ready<*>>(browser.catalog.settled()).value as List<*>
            assertEquals(1, catalog.size)

            browser.openThread(ThreadKey(technology.provider, technology.board.id, ThreadId(7)))
            val thread = assertIs<Load.Ready<Thread>>(browser.thread.settled()).value
            assertEquals("Hi", thread.subject)
            assertEquals(3, browser.backStack.value.size)

            assertTrue(browser.back())
            assertIs<Route.Catalog>(browser.backStack.value.last())
            assertTrue(browser.back())
            assertFalse(browser.back(), "the boards list is the bottom of the stack")

            assertEquals(setOf<String?>(ORBIN_USER_AGENT), userAgents)
            assertTrue("https://a.4cdn.org/g/thread/7.json" in requested)
        }

    @Test
    fun oneSiteDownStillListsTheOther() =
        runTest {
            val browser = browser(backgroundScope, BOTH_SITES - LYNXCHAN_BOARDS)

            val boards = assertIs<Load.Ready<List<SiteBoard>>>(browser.boards.settled()).value
            assertEquals(listOf("g"), boards.map { it.board.id.value })
        }

    @Test
    fun everySiteDownIsAnErrorThatRetryRecoversFrom() =
        runTest {
            val routes = mutableMapOf<String, String?>()
            val engine =
                MockEngine { request -> reply(request, routes) }
            val browser = Browser(orbinProviders(orbinHttpClient(engine)), bookmarks, history, backgroundScope)

            assertIs<Load.Failed>(browser.boards.settled())

            routes += BOTH_SITES
            browser.retry()
            assertIs<Load.Ready<*>>(browser.boards.settled())
        }

    @Test
    fun aMissingThreadIsAnErrorNotACrash() =
        runTest {
            val browser = browser(backgroundScope, BOTH_SITES)
            val board = assertIs<Load.Ready<List<SiteBoard>>>(browser.boards.settled()).value.first()

            browser.openThread(ThreadKey(board.provider, board.board.id, ThreadId(404)))
            assertIs<Load.Failed>(browser.thread.settled())
        }

    @Test
    fun goingBackShowsTheLoadedPageWithoutFetchingItAgain() =
        runTest {
            val browser = browser(backgroundScope, BOTH_SITES)
            val g =
                assertIs<Load.Ready<List<SiteBoard>>>(browser.boards.settled()).value.first {
                    it.board.id.value ==
                        "g"
                }
            browser.openBoard(g)
            browser.catalog.settled()
            browser.openThread(ThreadKey(g.provider, g.board.id, ThreadId(7)))
            browser.thread.settled()
            val before = requested.size

            browser.openMedia(0)
            assertEquals(Route.Media(ThreadKey(g.provider, g.board.id, ThreadId(7)), 0), browser.backStack.value.last())
            assertTrue(browser.back(), "viewer → thread")
            assertTrue(browser.back(), "thread → catalog")
            assertIs<Load.Ready<*>>(browser.catalog.value)
            assertEquals(before, requested.size, "nothing was fetched again")

            browser.retry()
            browser.catalog.settled()
            assertEquals(before + 1, requested.size, "retry always fetches")
        }

    @Test
    fun openingAThreadMarksItReadOnItsBoard() =
        runTest {
            val browser = browser(backgroundScope, BOTH_SITES)
            val g =
                assertIs<Load.Ready<List<SiteBoard>>>(browser.boards.settled()).value.first {
                    it.board.id.value ==
                        "g"
                }
            assertEquals(emptySet(), browser.visitedThreads(g).first())

            browser.openThread(ThreadKey(g.provider, g.board.id, ThreadId(7)))
            browser.thread.settled()

            assertEquals(setOf(7L), browser.visitedThreads(g).first { it.isNotEmpty() })
            assertEquals(
                NOW,
                history.entries.value.values
                    .single()
                    .lastVisitedMillis,
            )
        }

    @Test
    fun theWatchActionBookmarksTheThreadAndTakesItBackOff() =
        runTest {
            val browser = browser(backgroundScope, BOTH_SITES)
            val g =
                assertIs<Load.Ready<List<SiteBoard>>>(browser.boards.settled()).value.first {
                    it.board.id.value ==
                        "g"
                }
            val key = ThreadKey(g.provider, g.board.id, ThreadId(7))
            browser.openThread(key)
            val thread = assertIs<Load.Ready<Thread>>(browser.thread.settled()).value

            browser.toggleWatch(thread)
            assertTrue(browser.watching(key).first { it })
            assertEquals(
                "Hi",
                bookmarks.saved.value
                    .getValue(key)
                    .title,
            )

            browser.toggleWatch(thread)
            assertFalse(browser.watching(key).first { !it })
        }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val LYNXCHAN_BOARDS = "bbw-chan.link/boards.js"
        val BOTH_SITES =
            mapOf(
                "a.4cdn.org/boards.json" to """{"boards":[{"board":"g","title":"Technology"}]}""",
                "a.4cdn.org/g/catalog.json" to """[{"page":1,"threads":[{"no":7,"sub":"Hi","time":1}]}]""",
                "a.4cdn.org/g/thread/7.json" to """{"posts":[{"no":7,"sub":"Hi","time":1}]}""",
                LYNXCHAN_BOARDS to """{"status":"ok","data":{"boards":[{"boardUri":"b","boardName":"Random"}]}}""",
            )
    }
}
