package com.orbin.uinext

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the interface tells a screen reader. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class InterfaceSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the immersive feed offers no competing layout switcher`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS) }
        }
        composeRule.onNodeWithText("List").assertDoesNotExist()
        composeRule.onNodeWithText("Grid").assertDoesNotExist()
        composeRule.onNodeWithText("Images").assertDoesNotExist()
    }

    @Test
    fun `the feed refreshes by pulling down, not from a header button`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS) }
        }
        composeRule.onNodeWithText("Refresh").assertDoesNotExist()
    }

    @Test
    fun `a board catalog offers no layout switcher`() {
        composeRule.setContent {
            NextTheme {
                BoardScreen(
                    board = "/g/",
                    description = "Technology",
                    itemCount = ROWS.size,
                    rowAt = ROWS::getOrNull,
                )
            }
        }
        composeRule.onNodeWithText("Grid").assertDoesNotExist()
        composeRule.onNodeWithText("Images").assertDoesNotExist()
    }

    @Test
    fun `the feed caption opens the thread as an accessible button`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS) }
        }
        composeRule
            .onNode(hasTestTag(TikTokFeedTags.CAPTION) and hasText(ROWS[0].subject, substring = true))
            .assertHasClickAction()
            .assert(hasRole(Role.Button))
    }

    @Test
    fun `feed replies are a separate accessible action from the caption`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS) }
        }
        composeRule
            .onNodeWithContentDescription("View replies to ${ROWS[0].subject}")
            .assertHasClickAction()
        composeRule
            .onNode(
                hasTestTag(TikTokFeedTags.CAPTION) and hasText(ROWS[0].subject, substring = true),
            ).assert(hasTextContaining(ROWS[0].subject))
    }

    @Test
    fun `the immersive feed retains the current board identity with legacy grouping requested`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS, groupByBoard = true) }
        }
        composeRule
            .onNode(
                hasTestTag(TikTokFeedTags.CAPTION) and hasText(ROWS[0].subject, substring = true),
            ).assert(hasTextContaining(ROWS[0].board))
    }

    @Test
    fun `a board catalog names its board once and repeats it nowhere below`() {
        composeRule.setContent {
            NextTheme {
                BoardScreen(
                    board = "/g/",
                    description = "Technology",
                    itemCount = ROWS.size,
                    rowAt = ROWS::getOrNull,
                )
            }
        }
        composeRule.onAllNodesWithText("/g/", substring = true).assertCountEquals(1)
    }

    @Test
    fun `settings keeps uncategorized controls reachable from its directory`() {
        composeRule.setContent {
            NextTheme {
                SettingsScreen(
                    groups =
                        listOf(
                            "Privacy" to
                                listOf(
                                    SettingItem(id = "cache", label = "Image cache usage", value = "Clear"),
                                ),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Library").assertDoesNotExist()
        composeRule.onNodeWithText("All media").assertDoesNotExist()
        composeRule.onNodeWithText("Commands").assertDoesNotExist()
        composeRule.onNodeWithText("More settings").performClick()
        composeRule.onNodeWithText("Image cache usage").assertExists()
    }

    @Test
    fun `a thread shows its title once and keeps only the top and bottom jumps`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "Weekly desktop thread",
                    board = "/g/",
                    posts =
                        listOf(
                            Post(number = "1", time = "4m", body = "first"),
                            Post(number = "2", time = "3m", body = "second"),
                        ),
                )
            }
        }
        // The floating rail used to repeat the subject at the bottom of the screen.
        composeRule.onAllNodesWithText("Weekly desktop thread").assertCountEquals(1)
        composeRule.onNodeWithText("Top").assertExists()
        composeRule.onNodeWithText("Bottom").assertExists()
        composeRule.onNodeWithText("↓").assertDoesNotExist()
    }

    @Test
    fun `the five tabs expose home boards watched search and saved`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS, onOpenDownloads = {}, onOpenBoards = {}, onSettings = {}) }
        }
        listOf("Home", "Boards", "Watched", "Search", "Saved").forEach { label ->
            composeRule.onNode(hasContentDescription(label) and hasRole(Role.Tab)).assertExists()
        }
        composeRule.onNode(hasText("Downloads") and hasRole(Role.Tab)).assertDoesNotExist()
        composeRule.onNode(hasText("Settings") and hasRole(Role.Tab)).assertDoesNotExist()
    }

    @Test
    fun `settings opens from the feed options menu`() {
        var opened = 0
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS, onOpenDownloads = {}, onSettings = { opened++ }) }
        }
        composeRule.onNodeWithContentDescription("Feed options").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        assert(opened == 1) { "expected the header button to open settings" }
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun hasTextContaining(fragment: String) =
        SemanticsMatcher("text contains '$fragment'") { node ->
            node.config
                .getOrElse(SemanticsProperties.Text) { emptyList() }
                .any { it.text.contains(fragment) }
        }

    private companion object {
        val ROWS =
            listOf(
                FeedRow(
                    subject = "Anyone else running a home server on ARM?",
                    board = "/g/",
                    activity = "4m",
                    replies = 218,
                    media = 31,
                ),
                FeedRow(
                    subject = "Weekly desktop thread",
                    board = "/g/",
                    activity = "12m",
                    replies = 94,
                    media = 88,
                ),
            )
    }
}
