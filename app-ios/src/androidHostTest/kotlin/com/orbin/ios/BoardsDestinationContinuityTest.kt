package com.orbin.ios

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.graph.orbinProviders
import com.orbin.uinext.NextPlatform
import com.orbin.uinext.NextTheme
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the production directory binding, including source switching during partial loading. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BoardsDestinationContinuityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aPersistedSubscriptionIsSelectedAndCanBeUnfollowedWhileAnotherSourceIsLoading() =
        runTest {
            val waitingSource = CompletableDeferred<Unit>()
            val preferences =
                FakeBoardPreferences().apply {
                    subscribed.value = mapOf(FOURCHAN to setOf(BOARD))
                }
            val engine =
                MockEngine { request ->
                    if (request.url.host == "bbw-chan.link") {
                        waitingSource.await()
                        respond("", HttpStatusCode.NotFound)
                    } else if (request.url.encodedPath == "/boards.json") {
                        respond(
                            """{"boards":[{"board":"g","title":"Technology","ws_board":1}]}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            val browser =
                Browser(
                    orbinProviders(iosHttpClient(engine)),
                    FakeBookmarks(),
                    FakeHistory(),
                    preferences,
                    FakeSettings(),
                    backgroundScope,
                )
            browser.boardDirectory.first { it.boardsOnSite(FOURCHAN) is Load.Ready }
            browser.followed.first { FollowedBoard(FOURCHAN, BOARD) in it }
            browser.openTab(Route.Boards)
            composeRule.setContent {
                NextTheme(platform = NextPlatform.IOS) { BoardsDestination(browser) }
            }

            val remove = "Unfollow 4chan · /g/"
            composeRule.onAllNodesWithContentDescription(remove)[0].assertIsSelected().assertIsDisplayed()
            assertEquals(Load.Loading, browser.boards.value)

            val other = browser.sites.first { it.id != FOURCHAN }
            composeRule.onNodeWithText(other.name).performClick()
            runCurrent()
            browser.activeSite.first { it == other.id }
            composeRule.onNodeWithText("Loading…").assertIsDisplayed()
            composeRule.onNodeWithText("4chan").assertIsDisplayed().performClick()
            runCurrent()
            browser.activeSite.first { it == FOURCHAN }
            composeRule.onAllNodesWithContentDescription(remove)[0].assertIsSelected().assertIsDisplayed()

            // An independent star must not keep the subscription selected after Unfollow.
            preferences.favorites.value = mapOf(FOURCHAN to setOf(BOARD))
            runCurrent()
            composeRule.onAllNodesWithContentDescription(remove)[0].performClick()
            runCurrent()
            browser.followed.first { it.isEmpty() }
            assertTrue(
                preferences.subscribed.value[FOURCHAN]
                    .orEmpty()
                    .isEmpty(),
            )
            assertEquals(setOf(BOARD), preferences.favorites.value[FOURCHAN])
            composeRule.onAllNodesWithContentDescription("Follow 4chan · /g/")[0].assertIsDisplayed()
        }

    private companion object {
        val FOURCHAN = ProviderId("fourchan")
        val BOARD = BoardId("g")
    }
}
