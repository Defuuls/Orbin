package com.orbin.uinext

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.orbin.uinext.tokens.ReaderSize
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Drawer entries preserve the five primary destinations and expose Settings after a divider. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SocialNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Replaces `all five tabs dispatch distinct destinations`; Settings is also a real route. */
    @Test
    fun `all five roots and Settings dispatch once and close the drawer`() {
        val selected = mutableListOf<NextDestination>()
        composeRule.setContent {
            NextTheme {
                ReaderScaffold("Saved", destination = NextDestination.SAVED, onDestination = selected::add) {}
            }
        }

        drawerDestinations.forEach { destination ->
            composeRule.onNodeWithContentDescription("Menu").performClick()
            composeRule.onNodeWithTag(ReaderDrawerTags.ENTRY + destination.name).assertIsDisplayed().performClick()
            composeRule.onNodeWithTag(ReaderDrawerTags.DRAWER).assertIsNotDisplayed()
        }
        composeRule.runOnIdle { assertThat(selected).containsExactlyElementsIn(drawerDestinations).inOrder() }
    }

    /** Replaces the center add-button test: Boards is an ordered, selected drawer destination. */
    @Test
    fun `Boards entry opens once and announces selection in the ordered drawer`() {
        var calls = 0
        var selected: NextDestination? = null
        composeRule.setContent {
            NextTheme {
                ReaderDrawer(NextDestination.BOARDS, {
                    selected = it
                    calls++
                })
            }
        }

        val bounds =
            drawerDestinations.map { destination ->
                composeRule.onNodeWithTag(ReaderDrawerTags.ENTRY + destination.name).getUnclippedBoundsInRoot()
            }
        bounds.zipWithNext().forEach { (before, after) ->
            assertThat(before.bottom.value).isAtMost(after.top.value)
        }
        composeRule
            .onNodeWithTag(
                ReaderDrawerTags.ENTRY + NextDestination.BOARDS.name,
            ).assertIsSelected()
            .performClick()
        composeRule.runOnIdle {
            assertThat(selected).isEqualTo(NextDestination.BOARDS)
            assertThat(calls).isEqualTo(1)
        }
    }

    /** Replaces the tab decorative-label test with accessible, full-width drawer targets. */
    @Test
    fun `drawer entries expose labels selection and full touch bounds`() {
        composeRule.setContent {
            NextTheme { ReaderDrawer(NextDestination.FEED, {}) }
        }

        drawerDestinations
            .zip(
                listOf("Home", "Watched", "Boards", "Search", "Saved", "Settings"),
            ).forEach { (destination, label) ->
                composeRule
                    .onNodeWithTag(ReaderDrawerTags.ENTRY + destination.name)
                    .assertTextContains(label)
                    .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.HideFromAccessibility))
                    .assertWidthIsAtLeast(ReaderSize.touchTarget)
                    .assertHeightIsAtLeast(ReaderSize.touchTarget)
            }
        composeRule.onNodeWithTag(ReaderDrawerTags.ENTRY + NextDestination.FEED.name).assertIsSelected()
    }

    /** Replaces `secondary pages do not claim a primary tab` with real Back/Menu behavior. */
    @Test
    fun `secondary routes show Back and cannot open a drawer`() {
        var backs = 0
        var destinations = 0
        composeRule.setContent {
            NextTheme {
                ReaderScaffold(
                    "Settings",
                    destination = NextDestination.SETTINGS,
                    onDestination = { destinations++ },
                    onBack = { backs++ },
                ) {}
            }
        }

        composeRule.onNodeWithContentDescription("Menu").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.runOnIdle {
            assertThat(backs).isEqualTo(1)
            assertThat(destinations).isEqualTo(0)
        }
    }

    @Test
    fun `a root without a navigation callback omits Menu`() {
        composeRule.setContent {
            NextTheme { ReaderScaffold("Boards", destination = NextDestination.BOARDS) {} }
        }

        composeRule.onNodeWithContentDescription("Menu").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }
}

private val drawerDestinations =
    listOf(
        NextDestination.FEED,
        NextDestination.WATCHED,
        NextDestination.BOARDS,
        NextDestination.SEARCH,
        NextDestination.SAVED,
        NextDestination.SETTINGS,
    )
