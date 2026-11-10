package com.orbin.uinext

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Network state changes must leave a route out of a primary destination. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class PrimaryLoadingNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingBoardsKeepAllFiveTabsUsable() {
        var selected: NextDestination? = null
        composeRule.setContent {
            NextTheme {
                MessageScreen(
                    title = "Boards",
                    subtitle = "Loading…",
                    skeleton = true,
                    destination = NextDestination.BOARDS,
                    onDestination = { selected = it },
                )
            }
        }
        composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(5)
        composeRule.onNodeWithContentDescription("Watched").performClick()
        assertEquals(NextDestination.WATCHED, selected)
    }

    @Test
    fun failedHomeOffersRetryAndNavigationToAnotherTab() {
        var retries = 0
        var selected: NextDestination? = null
        composeRule.setContent {
            NextTheme {
                MessageScreen(
                    title = "Home",
                    subtitle = "Couldn't load the feed",
                    actionLabel = "Try again",
                    onAction = { retries++ },
                    destination = NextDestination.FEED,
                    onDestination = { selected = it },
                )
            }
        }
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
        composeRule.onNodeWithContentDescription("Saved").performClick()
        assertEquals(NextDestination.SAVED, selected)
    }
}
