package com.orbin.uinext

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    /** Replaces loadingBoardsKeepAllFiveTabsUsable: navigation now opens through Menu. */
    @Test
    fun loadingBoardsKeepAllDrawerDestinationsUsable() {
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

        composeRule.onNodeWithContentDescription("Menu").performClick()
        listOf(
            NextDestination.FEED,
            NextDestination.WATCHED,
            NextDestination.BOARDS,
            NextDestination.SEARCH,
            NextDestination.SAVED,
            NextDestination.SETTINGS,
        ).forEach { destination ->
            composeRule.onNodeWithTag(ReaderDrawerTags.ENTRY + destination.name).assertIsDisplayed()
        }
        composeRule.onNodeWithTag(ReaderDrawerTags.ENTRY + NextDestination.BOARDS.name).assertIsSelected()
        composeRule.onNodeWithTag(ReaderDrawerTags.ENTRY + NextDestination.WATCHED.name).performClick()
        composeRule.runOnIdle { assertEquals(NextDestination.WATCHED, selected) }
    }

    /** Replaces failedHomeOffersRetryAndNavigationToAnotherTab with the equivalent drawer route. */
    @Test
    fun failedHomeOffersRetryAndNavigationToAnotherDrawerDestination() {
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
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithTag(ReaderDrawerTags.ENTRY + NextDestination.SAVED.name).performClick()
        composeRule.runOnIdle { assertEquals(NextDestination.SAVED, selected) }
    }
}
