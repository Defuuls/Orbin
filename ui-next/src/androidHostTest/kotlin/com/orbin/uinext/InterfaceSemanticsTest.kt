package com.orbin.uinext

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import com.orbin.uinext.components.FeedRow
import com.orbin.uinext.components.NextDestination
import com.orbin.uinext.components.NextTheme
import com.orbin.uinext.components.OrbinNavigationTags
import com.orbin.uinext.screens.BoardScreen
import com.orbin.uinext.screens.BoardScreenActions
import com.orbin.uinext.screens.BoardScreenState
import com.orbin.uinext.screens.CatalogMode
import com.orbin.uinext.screens.FeedScreen
import com.orbin.uinext.screens.FeedScreenActions
import com.orbin.uinext.screens.FeedScreenState
import com.orbin.uinext.screens.Post
import com.orbin.uinext.screens.ReaderFeedTags
import com.orbin.uinext.screens.SettingItem
import com.orbin.uinext.screens.SettingsScreen
import com.orbin.uinext.screens.ThreadScreen
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
                FeedScreen(
                    state =
                        FeedScreenState(
                            rows = ROWS,
                        ),
                    actions =
                        FeedScreenActions(
                            onOpenBoards = {},
                            onModeChange = { chosen += it },
                        ),
                )
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
            NextTheme {
                FeedScreen(
                    state =
                        FeedScreenState(
                            rows = ROWS,
                        ),
                    actions = FeedScreenActions(),
                )
            }
        }
        composeRule.onNodeWithContentDescription("Reload").assertDoesNotExist()
    }

    @Test
    fun `a board catalog offers its other layout from the overflow menu`() {
        val chosen = mutableListOf<CatalogMode>()
        composeRule.setContent {
            NextTheme {
                BoardScreen(
                    state =
                        BoardScreenState(
                            board = "/g/",
                            description = "Technology",
                            itemCount = ROWS.size,
                            rowAt = ROWS::getOrNull,
                            mode = CatalogMode.LIST,
                        ),
                    actions =
                        BoardScreenActions(
                            onModeChange = { chosen += it },
                        ),
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
                    state =
                        BoardScreenState(
                            board = "/g/",
                            description = "Technology",
                            itemCount = ROWS.size,
                            rowAt = ROWS::getOrNull,
                        ),
                    actions = BoardScreenActions(),
                )
            }
        }
        composeRule.onNodeWithContentDescription("More options").assertDoesNotExist()
    }

    @Test
    fun `the reader row opens the thread as an accessible button`() {
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    state =
                        FeedScreenState(
                            rows = ROWS,
                        ),
                    actions = FeedScreenActions(),
                )
            }
        }
        composeRule
            .onNodeWithTag(ReaderFeedTags.row(ROWS[0].id))
            .assertHasClickAction()
            .assert(hasRole(Role.Button))
    }

    @Test
    fun `feed replies remain a separate accessible row action`() {
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    state =
                        FeedScreenState(
                            rows = ROWS,
                            repliesOpenComments = true,
                        ),
                    actions =
                        FeedScreenActions(
                            onOpenReplies = {},
                        ),
                )
            }
        }
        composeRule.onNodeWithTag(ReaderFeedTags.more(ROWS[0].id)).assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderFeedTags.row(ROWS[0].id)).performTouchInput { longClick() }
        composeRule.onNodeWithText("View replies to ${ROWS[0].subject}").assertHasClickAction()
    }

    @Test
    fun `reader rows retain the current board identity with legacy grouping requested`() {
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    state =
                        FeedScreenState(
                            rows = ROWS,
                        ),
                    actions = FeedScreenActions(),
                )
            }
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
                    state =
                        BoardScreenState(
                            board = "/g/",
                            description = "Technology",
                            itemCount = ROWS.size,
                            rowAt = ROWS::getOrNull,
                        ),
                    actions = BoardScreenActions(),
                )
            }
        }
        composeRule.onAllNodesWithText("/g/ - Technology").assertCountEquals(1)
        // The path appears in the heading only, not again on each row.
        composeRule.onAllNodesWithText("/g/", substring = true).assertCountEquals(1)
    }

    @Test
    fun `settings keeps uncategorized controls reachable by scrolling its grouped root`() {
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
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("reader-setting-row-cache"))
        composeRule.onNodeWithTag("reader-setting-row-cache").assertIsDisplayed().assertHasClickAction()
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

    @Test
    fun `the feed bar exposes only the wired primary destinations`() {
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    state =
                        FeedScreenState(
                            rows = ROWS,
                        ),
                    actions =
                        FeedScreenActions(
                            onOpenDownloads = {},
                            onOpenBoards = {},
                            onSettings = {},
                        ),
                )
            }
        }
        listOf(NextDestination.FEED, NextDestination.BOARDS, NextDestination.LIBRARY, NextDestination.SETTINGS)
            .forEach { destination ->
                composeRule.onNodeWithTag(OrbinNavigationTags.ENTRY + destination.name).assertExists()
            }
        composeRule.onNodeWithContentDescription("Menu").assertDoesNotExist()
    }

    @Test
    fun `settings opens from the feed bar`() {
        var opened = 0
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    state =
                        FeedScreenState(
                            rows = ROWS,
                        ),
                    actions =
                        FeedScreenActions(
                            onOpenDownloads = {},
                            onSettings = { opened++ },
                        ),
                )
            }
        }
        composeRule.onNodeWithTag(OrbinNavigationTags.ENTRY + NextDestination.SETTINGS.name).performClick()
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
