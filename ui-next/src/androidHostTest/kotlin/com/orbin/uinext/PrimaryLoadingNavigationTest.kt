package com.orbin.uinext

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.orbin.uinext.components.MessageScreen
import com.orbin.uinext.components.NextDestination
import com.orbin.uinext.components.NextTheme
import com.orbin.uinext.components.OrbinNavigationTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A loading or failed primary destination retains its Menu and every drawer route. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class PrimaryLoadingNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Replaces loadingBoardsKeepAllFiveTabsUsable: the bottom bar stays usable. */
    @Test
    fun loadingBoardsKeepThePrimaryBarUsable() {
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

        listOf(
            NextDestination.BOARDS,
            NextDestination.FEED,
            NextDestination.LIBRARY,
            NextDestination.SETTINGS,
        ).forEach { destination ->
            composeRule.onNodeWithTag(OrbinNavigationTags.ENTRY + destination.name).assertIsDisplayed()
        }
        composeRule.onNodeWithTag(OrbinNavigationTags.ENTRY + NextDestination.BOARDS.name).assertIsSelected()
        composeRule.onNodeWithTag(OrbinNavigationTags.ENTRY + NextDestination.LIBRARY.name).performClick()
        composeRule.runOnIdle { assertEquals(NextDestination.LIBRARY, selected) }
    }

    /** Replaces failedHomeOffersRetryAndNavigationToAnotherTab. */
    @Test
    fun failedFeedOffersRetryAndNavigationToAnotherTab() {
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
        composeRule.runOnIdle { assertEquals(1, retries) }
        composeRule.onNodeWithTag(OrbinNavigationTags.ENTRY + NextDestination.BOARDS.name).performClick()
        composeRule.runOnIdle { assertEquals(NextDestination.BOARDS, selected) }
    }
}
