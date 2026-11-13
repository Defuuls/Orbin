package com.orbin.uinext

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
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
    fun `Home offers its other reader layout only through a wired callback`() {
        val chosen = mutableListOf<CatalogMode>()
        composeRule.setContent {
            NextTheme {
                FeedScreen(rows = ROWS, onOpenBoards = {}, onModeChange = { chosen += it })
            }
        }
        composeRule.onNodeWithText("Grid view").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Grid view").performClick()
        assert(chosen == listOf(CatalogMode.GRID)) { "Home must expose its actual grid callback: $chosen" }
    }

    @Test
    fun `Home omits Reload when its refresh callback is not wired`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS) }
        }
        composeRule.onNodeWithContentDescription("Reload").assertDoesNotExist()
    }

    @Test
    fun `a board catalog offers its other layout from the overflow menu`() {
        val chosen = mutableListOf<CatalogMode>()
        composeRule.setContent {
            NextTheme {
                BoardScreen(
                    board = "/g/",
                    description = "Technology",
                    itemCount = ROWS.size,
                    rowAt = ROWS::getOrNull,
                    mode = CatalogMode.LIST,
                    onModeChange = { chosen += it },
                )
            }
        }
        composeRule.onNodeWithText("Grid view").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("List view").assertDoesNotExist()
        composeRule.onNodeWithText("Grid view").performClick()
        assert(chosen == listOf(CatalogMode.GRID)) { "expected one switch to the grid, got $chosen" }
    }

    @Test
    fun `a board catalog with nothing to offer draws no overflow button`() {
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
        composeRule.onNodeWithContentDescription("More options").assertDoesNotExist()
    }

    @Test
    fun `the reader row opens the thread as an accessible button`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS) }
        }
        composeRule
            .onNodeWithTag(ReaderFeedTags.row(ROWS[0].id))
            .assertHasClickAction()
            .assert(hasRole(Role.Button))
    }

    @Test
    fun `feed replies remain a separate accessible row action`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS, onOpenReplies = {}, repliesOpenComments = true) }
        }
        composeRule.onNodeWithTag(ReaderFeedTags.more(ROWS[0].id)).assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderFeedTags.row(ROWS[0].id)).performTouchInput { longClick() }
        composeRule.onNodeWithText("View replies to ${ROWS[0].subject}").assertHasClickAction()
    }

    @Test
    fun `reader rows retain the current board identity with legacy grouping requested`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS, groupByBoard = true) }
        }
        composeRule
            .onNodeWithTag(
                ReaderFeedTags.row(ROWS[0].id),
            ).assert(hasContentDescription(ROWS[0].board, substring = true))
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
        composeRule.onAllNodesWithText("/g/ - Technology").assertCountEquals(1)
        // The path appears in the heading only, not again on each row.
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
    fun `a thread shows its title once and jumps to the top from the app bar`() {
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
        composeRule.onAllNodesWithText("Weekly desktop thread").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Scroll to top").assertExists()
        composeRule.onNodeWithText("Top").assertDoesNotExist()
        composeRule.onNodeWithText("Bottom").assertDoesNotExist()
        composeRule.onNodeWithText("First new post").assertDoesNotExist()
    }

    /** Replaces `the five tabs expose home boards watched search and saved`; missing callbacks omit entries. */
    @Test
    fun `the drawer exposes only the wired root destinations and Settings`() {
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS, onOpenDownloads = {}, onOpenBoards = {}, onSettings = {}) }
        }
        composeRule.onNodeWithContentDescription("Menu").performClick()
        listOf(NextDestination.FEED, NextDestination.BOARDS, NextDestination.SETTINGS).forEach { destination ->
            composeRule.onNodeWithTag(ReaderDrawerTags.ENTRY + destination.name).assertExists()
        }
        listOf(
            NextDestination.WATCHED,
            NextDestination.SEARCH,
            NextDestination.SAVED,
            NextDestination.DOWNLOADS,
        ).forEach { destination ->
            composeRule.onNodeWithTag(ReaderDrawerTags.ENTRY + destination.name).assertDoesNotExist()
        }
    }

    @Test
    fun `settings opens from the reader drawer`() {
        var opened = 0
        composeRule.setContent {
            NextTheme { FeedScreen(rows = ROWS, onOpenDownloads = {}, onSettings = { opened++ }) }
        }
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithTag(ReaderDrawerTags.ENTRY + NextDestination.SETTINGS.name).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

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
