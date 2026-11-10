package com.orbin.uinext

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SocialFeedInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `same board on different sites retains its source identity`() {
        assertEquals("4chan · /g/", row("fourchan", "4chan").sourceAndBoard)
        assertEquals("BBWChan · /g/", row("bbwchan", "BBWChan").sourceAndBoard)
    }

    @Test
    fun `text posts do not compose a media placeholder`() {
        var previewRequested = false
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    rows = listOf(row("fourchan", "4chan").copy(hasPreview = false)),
                    thumbnail = { _, _ -> previewRequested = true },
                )
            }
        }
        composeRule.onNodeWithText("Weekly discussion").assertExists()
        composeRule.runOnIdle { assertTrue(!previewRequested) }
    }

    @Test
    fun `a source-qualified shortcut filters only that board and toggles off`() {
        val rows = listOf(row("fourchan", "4chan"), row("bbwchan", "BBWChan").copy(subject = "Another source"))
        composeRule.setContent { NextTheme { FeedScreen(rows = rows) } }
        composeRule.onNodeWithContentDescription("Filter by 4chan · /g/").performClick()
        composeRule.onNodeWithContentDescription("Filter by 4chan · /g/").assertIsSelected()
        composeRule.onNodeWithText("Another source").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Filter by 4chan · /g/").performClick()
        composeRule.onNodeWithText("Another source").assertExists()
    }

    @Test
    fun `favorite and board navigation invoke distinct callbacks`() {
        var opened = false
        var favorited = false
        composeRule.setContent {
            NextTheme {
                BoardsScreen(
                    boards = listOf(BoardTile("fourchan/g", "/g/", "Technology", site = "fourchan")),
                    onOpenBoard = { opened = true },
                    onFollowBoard = { _, checked -> favorited = checked },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Add 4chan · /g/ to favorite boards").performClick()
        composeRule.runOnIdle {
            assertTrue(favorited)
            assertTrue(!opened)
        }
        composeRule.onNodeWithText("/g/").performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun `feed actions remain independent from opening the thread`() {
        var opened = 0
        var watched = 0
        var saved = 0
        var shared = 0
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    rows = listOf(row("fourchan", "4chan")),
                    onOpenRow = { opened++ },
                    onToggleWatch = { watched++ },
                    onToggleSaved = { saved++ },
                    onShareRow = { shared++ },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Watch Weekly discussion").performClick()
        composeRule.onNodeWithContentDescription("Save Weekly discussion").performClick()
        composeRule.onNodeWithContentDescription("Share Weekly discussion").performClick()
        composeRule.runOnIdle {
            assertEquals(1, watched)
            assertEquals(1, saved)
            assertEquals(1, shared)
            assertEquals(0, opened)
        }
        composeRule.onNodeWithContentDescription("View replies to Weekly discussion").performClick()
        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun `a watched thread explains why its saved reference is retained`() {
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    rows = listOf(row("fourchan", "4chan").copy(watched = true, bookmarked = true)),
                    onToggleWatch = {},
                    onToggleSaved = {},
                )
            }
        }
        composeRule
            .onNodeWithContentDescription(
                "Saved while watching. Turn off Watch to remove Weekly discussion.",
            ).assertIsSelected()
            .assertIsNotEnabled()
            .assertHasNoClickAction()
    }

    private fun row(
        provider: String,
        label: String,
    ) = FeedRow(
        id = "$provider/g/1",
        subject = "Weekly discussion",
        board = "/g/",
        site = label,
        activity = "1m",
        replies = 2,
        media = 0,
        hasPreview = false,
    )
}
