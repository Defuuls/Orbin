package com.orbin.ios

import com.orbin.core.model.BoardId
import com.orbin.core.model.HomeFeedMode
import com.orbin.core.model.ProviderId
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.graph.orbinProviders
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Setup, the directory and Home share the same provider-qualified persisted follow membership. */
class BrowserBoardContinuityTest {
    @Test
    fun aSetupSubscriptionRemainsFollowedInBoardsAndAfterColdResume() =
        runTest {
            val preferences = FakeBoardPreferences()
            val settings = FakeSettings()
            val browser = browser(backgroundScope, preferences, settings)
            val board = browser.board(FOURCHAN)
            browser.firstRun.state.first { it.initialized }
            browser.setFollowed(board, true)
            browser.firstRun.state.first { it.canStart }
            browser.followed.first { it == setOf(FollowedBoard(FOURCHAN, BOARD)) }
            browser.completeFirstRun().join()
            browser.openTab(Route.Boards)

            val identity = FollowedBoard(FOURCHAN, BOARD)
            assertTrue(identity in browser.followed.value, "Boards must keep setup's subscription selected")
            assertTrue(browser.favorites.value.isEmpty(), "Following does not silently add an independent star")
            assertEquals(setOf(BOARD), preferences.subscribed.value[FOURCHAN])
            assertTrue(settings.settings.value.onboardingCompleted)

            val resumed = browser(backgroundScope, preferences, settings)
            assertFalse(
                resumed.firstRun.state
                    .first { it.initialized }
                    .needed,
            )
            assertEquals(setOf(identity), resumed.followed.first { it.isNotEmpty() })
            resumed.openTab(Route.Boards)
            assertEquals(setOf(identity), resumed.followed.value)
        }

    @Test
    fun unfollowRemovesTheChosenSourcesSubscriptionAndLegacyFavoriteOnly() =
        runTest {
            val preferences =
                FakeBoardPreferences().apply {
                    subscribed.value = mapOf(FOURCHAN to setOf(BOARD), BBWCHAN to setOf(BOARD))
                    favorites.value = subscribed.value
                }
            val browser = browser(backgroundScope, preferences, FakeSettings())
            val board = browser.board(FOURCHAN)
            browser.followed.first { it.size == 2 }
            browser.selectSite(BBWCHAN)
            browser.activeSite.first { it == BBWCHAN }
            browser.setFollowed(board, false)
            browser.firstRun.state.first { it.pendingWrites == 0 }
            val remaining = browser.followed.first { it == setOf(FollowedBoard(BBWCHAN, BOARD)) }

            assertEquals(setOf(FollowedBoard(BBWCHAN, BOARD)), remaining)
            assertTrue(
                preferences.subscribed.value[FOURCHAN]
                    .orEmpty()
                    .isEmpty(),
            )
            assertTrue(
                preferences.favorites.value[FOURCHAN]
                    .orEmpty()
                    .isEmpty(),
            )
            assertEquals(setOf(BOARD), preferences.subscribed.value[BBWCHAN])
            assertEquals(setOf(BOARD), preferences.favorites.value[BBWCHAN])

            val resumed = browser(backgroundScope, preferences, FakeSettings())
            assertEquals(remaining, resumed.followed.first { it.isNotEmpty() })
        }

    @Test
    fun queuedFollowEditsKeepTheirCapturedSourcesAndStartWaitsForEveryWrite() =
        runTest {
            val stored = FakeBoardPreferences()
            val firstWriteStarted = CompletableDeferred<Unit>()
            val allowFirstWrite = CompletableDeferred<Unit>()
            val writes = mutableListOf<String>()
            val preferences =
                object : BoardPreferencesRepository by stored {
                    override suspend fun setSubscribedBoard(
                        provider: ProviderId,
                        board: BoardId,
                        subscribed: Boolean,
                    ) {
                        if (writes.isEmpty()) {
                            firstWriteStarted.complete(Unit)
                            allowFirstWrite.await()
                        }
                        writes += "${provider.value}/${board.value}/$subscribed"
                        stored.setSubscribedBoard(provider, board, subscribed)
                    }

                    override suspend fun setFavoriteBoard(
                        provider: ProviderId,
                        board: BoardId,
                        favorite: Boolean,
                    ) {
                        writes += "star:${provider.value}/${board.value}/$favorite"
                        stored.setFavoriteBoard(provider, board, favorite)
                    }
                }
            val settings = FakeSettings()
            val browser = browser(backgroundScope, preferences, settings)
            val fourchan = browser.board(FOURCHAN)
            val bbwchan = browser.board(BBWCHAN)
            browser.firstRun.state.first { it.initialized }
            browser.setFollowed(fourchan, true)
            firstWriteStarted.await()
            browser.selectSite(BBWCHAN)
            browser.activeSite.first { it == BBWCHAN }
            browser.setFollowed(bbwchan, true)
            browser.selectSite(FOURCHAN)
            browser.activeSite.first { it == FOURCHAN }
            browser.setFollowed(fourchan, false)

            assertEquals(3, browser.firstRun.state.value.pendingWrites)
            assertFalse(browser.firstRun.state.value.canStart)
            browser.completeFirstRun().join()
            assertFalse(settings.settings.value.onboardingCompleted)
            allowFirstWrite.complete(Unit)
            browser.firstRun.state.first { it.canStart }

            assertEquals(
                listOf("fourchan/g/true", "bbwchan/g/true", "fourchan/g/false", "star:fourchan/g/false"),
                writes,
            )
            assertEquals(
                setOf(FollowedBoard(BBWCHAN, BOARD)),
                browser.followed.first { it == setOf(FollowedBoard(BBWCHAN, BOARD)) },
            )
            assertTrue(
                stored.subscribed.value[FOURCHAN]
                    .orEmpty()
                    .isEmpty(),
            )
            assertEquals(setOf(BOARD), stored.subscribed.value[BBWCHAN])
            browser.completeFirstRun().join()
            assertTrue(settings.settings.value.onboardingCompleted)
        }

    @Test
    fun restoredHideNsfwRequiresAnEligibleFollowBeforeSetupCanFinish() =
        runTest {
            val settings = FakeSettings().apply { settings.value = settings.value.copy(hideNsfwBoards = true) }
            val preferences = FakeBoardPreferences()
            val browser = browser(backgroundScope, preferences, settings)
            val hidden = browser.board(BBWCHAN)
            val visible = browser.board(FOURCHAN)
            assertTrue(hidden.board.isNsfw)
            assertFalse(visible.board.isNsfw)
            browser.firstRun.state.first { it.initialized }
            browser.setFollowed(hidden, true)
            browser.firstRun.state.first { it.canStart }
            browser.followed.first { it == setOf(FollowedBoard(BBWCHAN, BOARD)) }

            assertTrue(
                browser.boardDirectory.value
                    .eligibleFollowing(browser.followed.value, hideNsfw = true)
                    .isEmpty(),
            )
            browser.completeFirstRun().join()
            assertTrue(browser.firstRun.state.value.needed)
            assertFalse(settings.settings.value.onboardingCompleted)

            browser.setFollowed(visible, true)
            browser.firstRun.state.first { it.canStart }
            browser.followed.first { it.size == 2 }
            assertEquals(
                setOf(FollowedBoard(FOURCHAN, BOARD)),
                browser.boardDirectory.value.eligibleFollowing(browser.followed.value, hideNsfw = true),
            )
            browser.completeFirstRun().join()

            assertFalse(browser.firstRun.state.value.needed)
            assertTrue(settings.settings.value.onboardingCompleted)
            assertEquals(setOf(BOARD), preferences.subscribed.value[BBWCHAN], "A hidden source choice remains durable")
            assertEquals(setOf(BOARD), preferences.subscribed.value[FOURCHAN])
        }

    @Test
    fun aReadySourceCanFinishSetupWhileAnotherSourceLoadsThenFails() =
        runTest {
            val allowOtherSource = CompletableDeferred<Unit>()
            val engine =
                MockEngine { request ->
                    if (request.url.host == "bbw-chan.link") {
                        allowOtherSource.await()
                        respond("", HttpStatusCode.NotFound)
                    } else {
                        val json = if (request.url.encodedPath == "/boards.json") FOURCHAN_BOARDS else FOURCHAN_CATALOG
                        respond(json, HttpStatusCode.OK, JSON_HEADERS)
                    }
                }
            val preferences = FakeBoardPreferences()
            val settings = FakeSettings()
            val browser = browser(backgroundScope, preferences, settings, engine)
            val board = browser.board(FOURCHAN)

            assertIs<Load.Loading>(browser.boards.value, "The aggregate still waits for every provider")
            assertIs<Load.Loading>(browser.boardDirectory.value.boardsOnSite(BBWCHAN))
            browser.firstRun.state.first { it.initialized }
            browser.setFollowed(board, true)
            browser.firstRun.state.first { it.canStart }
            browser.followed.first { it == setOf(FollowedBoard(FOURCHAN, BOARD)) }
            browser.selectFeedMode(HomeFeedMode.FOLLOWING)
            browser.completeFirstRun().join()

            assertFalse(browser.firstRun.state.value.needed)
            assertTrue(settings.settings.value.onboardingCompleted)
            val feed =
                assertIs<Load.Ready<List<FeedThread>>>(
                    browser.feed.first {
                        it is Load.Ready &&
                            it.value.isNotEmpty()
                    },
                )
            assertEquals(listOf(FOURCHAN), feed.value.map { it.provider })
            allowOtherSource.complete(Unit)
            val directory = browser.boardDirectory.first { BBWCHAN in it.failures }

            assertEquals(listOf(board), assertIs<Load.Ready<List<SiteBoard>>>(directory.boardsOnSite(FOURCHAN)).value)
            assertIs<Load.Failed>(directory.boardsOnSite(BBWCHAN))
            assertEquals(
                listOf(board),
                assertIs<Load.Ready<List<SiteBoard>>>(browser.boards.first { it is Load.Ready }).value,
            )
            assertEquals(setOf(FollowedBoard(FOURCHAN, BOARD)), browser.followed.value)
        }

    private suspend fun Browser.board(provider: ProviderId): SiteBoard =
        assertIs<Load.Ready<List<SiteBoard>>>(
            boardDirectory.first { it.boards.any { board -> board.provider == provider } }.boardsOnSite(provider),
        ).value.single()

    private fun browser(
        scope: CoroutineScope,
        preferences: BoardPreferencesRepository,
        settings: FakeSettings,
        engine: MockEngine =
            MockEngine { request ->
                val json =
                    when {
                        request.url.host == "bbw-chan.link" -> BBWCHAN_BOARDS
                        request.url.encodedPath == "/boards.json" -> FOURCHAN_BOARDS
                        else -> FOURCHAN_CATALOG
                    }
                respond(json, HttpStatusCode.OK, JSON_HEADERS)
            },
    ): Browser =
        Browser(
            orbinProviders(iosHttpClient(engine)),
            FakeBookmarks(),
            FakeHistory(),
            preferences,
            settings,
            scope,
        )

    private companion object {
        val FOURCHAN = ProviderId("fourchan")
        val BBWCHAN = ProviderId("bbwchan")
        val BOARD = BoardId("g")
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
        const val FOURCHAN_BOARDS = """{"boards":[{"board":"g","title":"Technology","ws_board":1}]}"""
        const val BBWCHAN_BOARDS = """{"status":"ok","data":{"boards":[{"boardUri":"g","boardName":"General"}]}}"""
        const val FOURCHAN_CATALOG =
            """[{"page":1,"threads":[{"no":7,"sub":"Technology","time":1,"tim":7,"ext":".mp4"}]}]"""
    }
}
