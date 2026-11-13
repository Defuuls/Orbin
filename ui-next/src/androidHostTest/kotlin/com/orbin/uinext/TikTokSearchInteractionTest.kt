package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TikTokSearchInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun submitAndKeyboardSearchUseTheCurrentQueryAndClearRemainsSeparate() {
        val submitted = mutableListOf<String>()
        var query = ""
        var backs = 0
        composeRule.setContent {
            var text by remember { mutableStateOf("") }
            NextTheme {
                SearchScreen(
                    query = text,
                    onQueryChange = {
                        text = it
                        query = it
                    },
                    onSearch = { submitted += text },
                    state = SearchState.Idle,
                    onOpenRow = {},
                    onBack = { backs++ },
                )
            }
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("webm")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        composeRule.onNodeWithText("Search").performClick()
        assertEquals(listOf("webm", "webm"), submitted)
        composeRule.onNodeWithContentDescription("Clear search").performClick()
        assertEquals("", query)
        assertEquals(2, submitted.size)
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun sourceTabsKeepEqualBoardAndThreadNumbersOnDifferentSitesDistinct() {
        val first = SearchRow("fourchan/g/41", "First site thread", "4chan · /g/", "First source")
        val second = SearchRow("bbwchan/g/41", "Second site thread", "BBWChan · /g/", "Second source")
        val legacy = SearchRow("preview", "Legacy preview", "/g/", "Unqualified preview remains in All")
        var opened: SearchRow? = null
        composeRule.setContent {
            NextTheme {
                SearchScreen(
                    query = "thread",
                    onQueryChange = {},
                    onSearch = {},
                    state = SearchState.Results(listOf(first, second, legacy)),
                    onOpenRow = { opened = it },
                )
            }
        }
        composeRule.onNodeWithText("BBWChan").performClick()
        composeRule.onNodeWithText(first.title).assertDoesNotExist()
        composeRule.onNodeWithText(legacy.title).assertDoesNotExist()
        composeRule.onNodeWithText(second.title).performClick()
        assertEquals(second, opened)
        composeRule.onNodeWithText("All").performClick()
        composeRule.onNodeWithText(first.title).assertExists()
        composeRule.onNodeWithText(legacy.title).assertExists()
    }

    @Test
    fun selectedSourceSurvivesLoadingWhenTheNewResultsStillContainIt() {
        val first = SearchRow("fourchan/g/41", "First site thread", "4chan · /g/", "First source")
        val second = SearchRow("bbwchan/g/41", "Second site thread", "BBWChan · /g/", "Second source")
        var state: SearchState by mutableStateOf(SearchState.Results(listOf(first, second)))
        composeRule.setContent {
            NextTheme {
                SearchScreen("thread", {}, {}, state, {})
            }
        }
        composeRule.onNodeWithText("BBWChan").performClick()
        composeRule.runOnIdle { state = SearchState.Loading }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state = SearchState.Results(listOf(first, second)) }
        composeRule.onNodeWithText(first.title).assertDoesNotExist()
        composeRule.onNodeWithText(second.title).assertExists()
    }

    @Test
    fun aFailedSearchKeepsItsMessageAndOffersARealRetry() {
        var retries = 0
        composeRule.setContent {
            NextTheme {
                SearchScreen("thread", {}, { retries++ }, SearchState.Error("Source temporarily unavailable"), {})
            }
        }
        composeRule.onNodeWithText("Source temporarily unavailable").assertExists()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun narrowLargeTextKeepsTheFieldAndSubmitActionInsideTheViewport() {
        composeRule.setContent {
            Box(Modifier.width(280.dp)) {
                NextTheme(fontScale = 2f) {
                    SearchScreen(
                        query = "video",
                        onQueryChange = {},
                        onSearch = {},
                        state = SearchState.Idle,
                        onOpenRow = {},
                        onBack = {},
                    )
                }
            }
        }
        val field = composeRule.onNode(hasSetTextAction()).getUnclippedBoundsInRoot()
        val submit = composeRule.onNodeWithText("Search").getUnclippedBoundsInRoot()
        assertTrue(field.bottom - field.top >= 48.dp)
        assertTrue(submit.top >= field.bottom)
        assertTrue(submit.left >= 0.dp && submit.right <= 280.dp)
    }
}
