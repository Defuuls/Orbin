package com.orbin.ios

import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.graph.orbinProviders
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The chat's media destination fetches a complete thread without first opening its comments. */
class BrowserThreadVideosTest {
    @Test
    fun videosOpenDirectlyFromHomeAndReturnToHomeWithoutACommentsPage() =
        runTest {
            val fixture = Fixture()
            val browser = fixture.browser(backgroundScope)
            browser.firstRun.state.first { it.initialized && !it.needed }

            browser.openThreadVideos(fixture.key)

            val loaded = assertIs<Load.Ready<Thread>>(browser.thread.first { it !is Load.Loading }).value
            assertEquals(fixture.key, loaded.key)
            assertEquals(3, loaded.files.size, "The provider loads attachments on the OP and every reply")
            assertEquals(listOf(Route.Feed, Route.ThreadVideos(fixture.key)), browser.backStack.value)
            assertFalse(browser.backStack.value.any { it is Route.ThreadPage })
            assertTrue(fixture.key in fixture.history.entries.value)
            assertTrue(browser.back())
            assertEquals(listOf(Route.Feed), browser.backStack.value)
        }

    @Test
    fun aFailedVideoThreadStaysOnItsDestinationAndCanBeRetried() =
        runTest {
            val fixture = Fixture()
            fixture.threadAvailable = false
            val browser = fixture.browser(backgroundScope)
            browser.firstRun.state.first { it.initialized && !it.needed }

            browser.openThreadVideos(fixture.key)
            assertIs<Load.Failed>(browser.thread.first { it !is Load.Loading })
            assertEquals(Route.ThreadVideos(fixture.key), browser.backStack.value.last())

            fixture.threadAvailable = true
            browser.retry()

            assertEquals(
                fixture.key,
                assertIs<Load.Ready<Thread>>(browser.thread.first { it !is Load.Loading }).value.key,
            )
            assertEquals(2, fixture.threadRequests)
            assertFalse(browser.backStack.value.any { it is Route.ThreadPage })
        }

    @Test
    fun aThreadWithNoVideosStillLoadsItsOwnDestinationWithoutOpeningComments() =
        runTest {
            val fixture = Fixture()
            fixture.body =
                """{"posts":[{"no":7,"resto":0,"time":1700000000,"com":"Text only"}]}"""
            val browser = fixture.browser(backgroundScope)
            browser.firstRun.state.first { it.initialized && !it.needed }

            browser.openThreadVideos(fixture.key)

            assertTrue(assertIs<Load.Ready<Thread>>(browser.thread.first { it !is Load.Loading }).value.files.isEmpty())
            assertEquals(Route.ThreadVideos(fixture.key), browser.backStack.value.last())
            assertTrue(browser.back())
            assertEquals(Route.Feed, browser.backStack.value.single())
        }

    @Test
    fun openingCommentsExplicitlyRetainsTheOriginalThreadPageRoute() =
        runTest {
            val fixture = Fixture()
            val browser = fixture.browser(backgroundScope)
            browser.firstRun.state.first { it.initialized && !it.needed }

            browser.openThread(fixture.key)

            assertIs<Load.Ready<Thread>>(browser.thread.first { it !is Load.Loading })
            assertEquals(listOf(Route.Feed, Route.ThreadPage(fixture.key)), browser.backStack.value)
            assertFalse(browser.backStack.value.any { it is Route.ThreadVideos })
        }

    private class Fixture {
        val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(7))
        val history = FakeHistory()
        var threadAvailable = true
        var threadRequests = 0
        var body = THREAD

        fun browser(scope: kotlinx.coroutines.CoroutineScope): Browser {
            val engine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    if (path == "/g/thread/7.json") threadRequests++
                    val json =
                        when (path) {
                            "/boards.json" -> BOARDS
                            "/g/catalog.json" -> "[]"
                            "/g/thread/7.json" -> body.takeIf { threadAvailable }
                            else -> null
                        }
                    respond(
                        json.orEmpty(),
                        if (json == null) HttpStatusCode.NotFound else HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val preferences =
                FakeBoardPreferences().apply {
                    subscribed.value =
                        mapOf(
                            key.provider to setOf(key.board),
                        )
                }
            return Browser(
                orbinProviders(iosHttpClient(engine)).filter { it.metadata.id == key.provider },
                FakeBookmarks(),
                history,
                preferences,
                FakeSettings().apply { settings.value = settings.value.copy(onboardingCompleted = true) },
                scope,
            )
        }
    }

    private companion object {
        const val BOARDS = """{"boards":[{"board":"g","title":"Technology","ws_board":1}]}"""
        const val THREAD = """{"posts":[
            {"no":7,"resto":0,"time":1700000000,"sub":"Media thread","filename":"op","tim":100,"ext":".jpg","w":80,"h":80},
            {"no":8,"resto":7,"time":1700000001,"filename":"reply","tim":101,"ext":".webm","w":80,"h":80},
            {"no":9,"resto":7,"time":1700000002,"filename":"second","tim":102,"ext":".mp4","w":80,"h":80}
        ]}"""
    }
}
