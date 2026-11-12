package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class ReaderHomeAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun narrowMaximumTextKeepsSourceChoiceAndRowActionsAccessible() {
        var source by mutableIntStateOf(0)
        compose.setContent {
            NextTheme(fontScale = 2f) {
                Box(Modifier.size(248.dp, 700.dp)) {
                    FeedScreen(
                        rows = listOf(ROW),
                        sourceLabels = listOf("All followed sites", "4chan", "BBWChan"),
                        selectedSource = source,
                        onSelectSource = { source = it },
                        onOpenBoards = {},
                        onToggleWatch = {},
                        onToggleSaved = {},
                    )
                }
            }
        }
        compose
            .onNodeWithContentDescription("Choose source")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        assertCompleteLabel("All followed sites")
        compose.onNodeWithContentDescription("Choose source").performClick()
        compose
            .onNode(
                hasText("BBWChan") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
            ).performClick()
        compose
            .onNodeWithContentDescription("Choose source")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "BBWChan"))
        val bar = compose.onNodeWithTag(ReaderAppBarTags.BAR).getUnclippedBoundsInRoot()
        val sourceBounds = compose.onNodeWithContentDescription("Choose source").getUnclippedBoundsInRoot()
        assertTrue("Source choice must stay below the fixed app bar", sourceBounds.top >= bar.bottom)
        compose.onNodeWithTag(ReaderFeedTags.FOLLOWING).assertDoesNotExist()
        compose.onNodeWithTag(ReaderFeedTags.FOR_YOU).assertDoesNotExist()
        compose
            .onNodeWithTag(ReaderFeedTags.more(ROW.id))
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        compose.onNodeWithText("Watch ${ROW.subject}").assertIsDisplayed()
        compose.onNodeWithText("Save ${ROW.subject}").assertIsDisplayed()
    }

    @Test
    fun rowAndMediaActionsAreSeparateAccessibleControls() {
        var opened = 0
        var media = 0
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = listOf(ROW),
                    onOpenRow = { opened++ },
                    onOpenMedia = { media++ },
                    thumbnail = { _, modifier -> Box(modifier) },
                )
            }
        }
        compose
            .onNodeWithTag(
                ReaderFeedTags.thumbnail(ROW.id),
                useUnmergedTree = true,
            ).performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals(1, media)
            assertEquals(0, opened)
        }
        compose.onNodeWithTag(ReaderFeedTags.row(ROW.id)).performClick()
        compose.runOnIdle {
            assertEquals(1, media)
            assertEquals(1, opened)
        }
        val mediaAction =
            compose
                .onNodeWithTag(ReaderFeedTags.row(ROW.id))
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .single { it.label == "Open media in ${ROW.subject}" }
        compose.runOnIdle {
            assertTrue(mediaAction.action())
            assertEquals(2, media)
            assertEquals(1, opened)
        }
    }

    @Test
    fun coveredMediaRevealsBeforeTheExplicitMediaRouteOpens() {
        var opened = 0
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = listOf(ROW.copy(imageSpoiler = true)),
                    onOpenMedia = { opened++ },
                    thumbnail = { _, modifier -> Box(modifier) },
                )
            }
        }
        compose
            .onNodeWithTag(
                ReaderFeedTags.thumbnail(ROW.id),
                useUnmergedTree = true,
            ).performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(0, opened) }
        compose
            .onNodeWithTag(
                ReaderFeedTags.thumbnail(ROW.id),
                useUnmergedTree = true,
            ).performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun unwiredOptionalActionsDoNotLeaveAnOverflowControl() {
        compose.setContent {
            NextTheme { FeedScreen(rows = listOf(ROW), thumbnail = { _, modifier -> Box(modifier) }) }
        }
        compose.onNodeWithTag(ReaderFeedTags.row(ROW.id)).assertIsDisplayed()
        compose.onNodeWithTag(ReaderFeedTags.more(ROW.id)).assertDoesNotExist()
        compose.onNodeWithText("Open media in ${ROW.subject}").assertDoesNotExist()
        compose.onNodeWithText("Watch ${ROW.subject}").assertDoesNotExist()
        compose.onNodeWithText("Save ${ROW.subject}").assertDoesNotExist()
    }

    private fun assertCompleteLabel(label: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose
            .onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.size)
        val layout = layouts.single()
        assertFalse("$label must not overflow at maximum text", layout.hasVisualOverflow)
        repeat(layout.lineCount) { line -> assertFalse(layout.isLineEllipsized(line)) }
        assertEquals(label.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
    }

    private companion object {
        val ROW =
            FeedRow(
                id = "fourchan/g/7",
                subject = "Accessibility fixture",
                board = "/g/",
                site = "4chan",
                activity = "1m",
                replies = 12,
                media = 1,
                mediaLabel = "MP4",
            )
    }
}
