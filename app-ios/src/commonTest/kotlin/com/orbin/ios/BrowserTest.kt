package com.orbin.ios

import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.ProviderId
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The iOS browser end to end over the real providers, with the network replaced by a script. */
class BrowserTest {
    private val requested = mutableListOf<String>()
    private val userAgents = mutableSetOf<String?>()
    private val recording = Mutex()
    private val bookmarks = FakeBookmarks()
    private val history = FakeHistory()
    private val boardPreferences = FakeBoardPreferences()

    private var clock = NOW

    private fun browser(
        scope: CoroutineScope,
        routes: Map<String, String?>,
    ): Browser {
        val engine =
            MockEngine { request ->
                // The engine answers on several threads at once (both sites load together).
                recording.withLock {
                    requested += request.url.toString()
                    userAgents += request.headers[HttpHeaders.UserAgent]
                }
                reply(request, routes)
            }
        return Browser(
            orbinProviders(orbinHttpClient(engine)),
            bookmarks,
            history,
            boardPreferences,
            scope,
            now = { clock },
        )
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
            assertFalse(browser.back(), "the start tab is the bottom of the stack")

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
            val browser =
                Browser(orbinProviders(orbinHttpClient(engine)), bookmarks, history, boardPreferences, backgroundScope)

            assertIs<Load.Failed>(browser.boards.settled())

            routes += BOTH_SITES
            browser.openTab(Route.Boards)
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

            browser.watched.toggle(thread)
            assertTrue(browser.watched.watching(key).first { it })
            assertEquals(
                "Hi",
                bookmarks.saved.value
                    .getValue(key)
                    .title,
            )

            browser.watched.toggle(thread)
            assertFalse(browser.watched.watching(key).first { !it })
        }

    @Test
    fun followedBoardsOnEverySiteMakeOneFeedSortedByBoard() =
        runTest {
            val browser = browser(backgroundScope, BOTH_SITES + LYNXCHAN_CATALOG)
            val boards = assertIs<Load.Ready<List<SiteBoard>>>(browser.boards.settled()).value
            boards.forEach { browser.setFollowed(it, follow = true) }

            val feed = browser.feed.first { it is Load.Ready && it.value.size == 2 }
            val threads = assertIs<Load.Ready<List<FeedThread>>>(feed).value
            assertEquals(listOf("b", "g"), threads.map { it.thread.key.board.value }, "board A–Z, across sites")
            assertEquals(2, threads.map { it.provider }.toSet().size)
            assertEquals(Route.Feed, browser.backStack.value.single(), "the feed is the start screen")
        }

    @Test
    fun aFollowedBoardThatFailsLeavesTheOthersInTheFeed() =
        runTest {
            val browser = browser(backgroundScope, BOTH_SITES)
            val boards = assertIs<Load.Ready<List<SiteBoard>>>(browser.boards.settled()).value
            // The LynxChan /b/ catalog is not in the script, so it fails with a 404.
            boards.forEach { browser.setFollowed(it, follow = true) }

            val feed = browser.feed.first { it is Load.Ready && it.value.isNotEmpty() }
            assertEquals(
                listOf("g"),
                assertIs<Load.Ready<List<FeedThread>>>(feed).value.map { it.thread.key.board.value },
            )
        }

    @Test
    fun aBoardsFeedLimitCutsItsThreads() =
        runTest {
            val manyThreads = (1..8).joinToString(",") { """{"no":$it,"sub":"T$it","time":$it}""" }
            val routes = BOTH_SITES + ("a.4cdn.org/g/catalog.json" to """[{"page":1,"threads":[$manyThreads]}]""")
            val browser = browser(backgroundScope, routes)
            val g =
                assertIs<Load.Ready<List<SiteBoard>>>(browser.boards.settled()).value.first {
                    it.board.id.value ==
                        "g"
                }
            boardPreferences.setFeedThreadLimit(g.provider, g.board.id, FeedThreadLimit.SIX)

            browser.setFollowed(g, follow = true)

            val feed = browser.feed.first { it is Load.Ready && it.value.isNotEmpty() }
            assertEquals(6, assertIs<Load.Ready<List<FeedThread>>>(feed).value.size)
        }

    @Test
    fun withNothingFollowedTheFeedIsEmptyNotAnError() =
        runTest {
            val browser = browser(backgroundScope, BOTH_SITES)
            assertEquals(Load.Ready(emptyList()), browser.feed.settled())
        }

    @Test
    fun watchedThreadsCatchUpAtLaunchAndShowTheirNewRepliesOnTheirBoard() =
        runTest {
            bookmarks.addBookmark(watchedBookmark(seen = 1))
            val browser = browser(backgroundScope, BOTH_SITES + (WATCHED_THREAD to threadWithReplies(3)))
            val g = boardG(browser)

            assertEquals(mapOf(7L to 2), browser.watched.unread(g).first { it.isNotEmpty() })
        }

    @Test
    fun openingAWatchedThreadReadsItAndPointsAtTheFirstNewReply() =
        runTest {
            bookmarks.addBookmark(watchedBookmark(seen = 1))
            val browser = browser(backgroundScope, BOTH_SITES + (WATCHED_THREAD to threadWithReplies(3)))
            val g = boardG(browser)

            browser.openThread(WATCHED_KEY)
            browser.thread.settled()

            assertEquals("9", browser.firstUnreadPostId.value, "replies 8, 9 and 10, of which 8 was seen")
            assertEquals(emptyMap(), browser.watched.unread(g).first { it.isEmpty() })
            assertEquals(
                3,
                bookmarks.saved.value
                    .getValue(WATCHED_KEY)
                    .lastSeenReplyCount,
            )
        }

    @Test
    fun anUnwatchedThreadHasNothingToJumpTo() =
        runTest {
            val browser = browser(backgroundScope, BOTH_SITES + (WATCHED_THREAD to threadWithReplies(3)))
            boardG(browser)

            browser.openThread(WATCHED_KEY)
            browser.thread.settled()

            assertNull(browser.firstUnreadPostId.value)
        }

    @Test
    fun theWatchRefreshRunsAtMostOnceInItsInterval() =
        runTest {
            bookmarks.addBookmark(watchedBookmark(seen = 1))
            val routes = (BOTH_SITES + (WATCHED_THREAD to threadWithReplies(3))).toMutableMap()
            val browser = browser(backgroundScope, routes)
            browser.watched.refresh().join() // the one started at launch
            assertEquals(
                3,
                bookmarks.saved.value
                    .getValue(WATCHED_KEY)
                    .latestReplyCount,
            )

            routes[WATCHED_THREAD] = threadWithReplies(5)
            browser.openTab(Route.Boards)
            browser.watched.refresh().join()
            assertEquals(
                3,
                bookmarks.saved.value
                    .getValue(WATCHED_KEY)
                    .latestReplyCount,
                "not again so soon",
            )

            clock += WATCH_REFRESH_INTERVAL_MS
            browser.watched.refresh().join()
            assertEquals(
                5,
                bookmarks.saved.value
                    .getValue(WATCHED_KEY)
                    .latestReplyCount,
            )
        }

    private suspend fun boardG(browser: Browser): SiteBoard =
        assertIs<Load.Ready<List<SiteBoard>>>(browser.boards.settled()).value.first { it.board.id.value == "g" }

    private fun watchedBookmark(seen: Int) =
        Bookmark(
            key = WATCHED_KEY,
            title = "Hi",
            createdAtMillis = 1L,
            isWatched = true,
            lastSeenReplyCount = seen,
            latestReplyCount = seen,
        )

    private companion object {
        val LYNXCHAN_CATALOG = "bbw-chan.link/b/catalog.json" to """[{"threadId":5,"subject":"Hi"}]"""
        const val NOW = 1_700_000_000_000L
        const val WATCHED_THREAD = "a.4cdn.org/g/thread/7.json"
        val WATCHED_KEY = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(7))

        /** Thread 7 with [count] replies, numbered from 8, and the reply count 4chan reports on the OP. */
        fun threadWithReplies(count: Int): String {
            val replies = (8 until 8 + count).joinToString("") { """,{"no":$it,"time":$it}""" }
            return """{"posts":[{"no":7,"sub":"Hi","time":1,"replies":$count}$replies]}"""
        }

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
