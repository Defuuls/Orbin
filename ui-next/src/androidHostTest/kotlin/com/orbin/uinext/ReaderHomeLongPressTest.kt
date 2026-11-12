package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config

/** Home's list rows and grid cards share their options without a separate visual control. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class ReaderHomeLongPressTest(
    private val mode: CatalogMode,
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun touchLongPressRetainsEveryMenuCallbackAndConsumesTheThreadTap() {
        val events = mutableListOf<Pair<String, String>>()
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = listOf(ROW),
                    mode = mode,
                    thumbnail = { _, modifier -> Box(modifier) },
                    onOpenRow = { events += "thread" to it.id },
                    onOpenMedia = { events += "media" to it.id },
                    onOpenReplies = { events += "videos" to it.id },
                    onOpenBoard = { events += "board" to it.id },
                    onToggleWatch = { events += "watch" to it.id },
                    onToggleSaved = { events += "save" to it.id },
                    onShareRow = { events += "share" to it.id },
                )
            }
        }
        compose.onNodeWithTag(ReaderFeedTags.more(ROW.id)).assertDoesNotExist()
        val choices =
            listOf(
                "Open media in ${ROW.subject}" to "media",
                "View videos in ${ROW.subject}" to "videos",
                "Open ${ROW.board} board" to "board",
                "Watch ${ROW.subject}" to "watch",
                "Save ${ROW.subject}" to "save",
                "Share ${ROW.subject}" to "share",
            )
        choices.forEach { (label, _) ->
            openWithTouch()
            choices.forEach { (option, _) -> compose.onNodeWithText(option).assertIsDisplayed() }
            compose.onNodeWithText(label).performClick()
        }
        compose.runOnIdle {
            assertEquals(choices.map { it.second to ROW.id }, events)
        }
        compose.onNodeWithTag(ReaderFeedTags.row(ROW.id)).performTouchInput {
            click(Offset(width - 8f, height - 8f))
        }
        compose.runOnIdle {
            assertEquals(choices.map { it.second to ROW.id } + ("thread" to ROW.id), events)
        }
    }

    @Test
    fun screenReadersRetainLabeledLongClickAndDirectCustomActions() {
        var watched = 0
        var opened = 0
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = listOf(ROW),
                    mode = mode,
                    onOpenRow = { opened++ },
                    onToggleWatch = { watched++ },
                )
            }
        }
        val row = compose.onNodeWithTag(ReaderFeedTags.row(ROW.id))
        val semantics = row.fetchSemanticsNode().config
        assertEquals("Options for ${ROW.subject}", semantics[SemanticsActions.OnLongClick].label)
        val watch = semantics[SemanticsActions.CustomActions].single { it.label == "Watch ${ROW.subject}" }
        compose.runOnIdle {
            assertTrue(watch.action())
            assertEquals(1, watched)
            assertEquals(0, opened)
        }
        row.performSemanticsAction(SemanticsActions.OnLongClick) { assertTrue(it()) }
        compose.onNodeWithText("Watch ${ROW.subject}").performClick()
        compose.runOnIdle {
            assertEquals(2, watched)
            assertEquals(0, opened)
        }
        compose.onNodeWithTag(ReaderFeedTags.more(ROW.id)).assertDoesNotExist()
    }

    @Test
    fun spoilerMenuRequiresRevealBeforeOfferingMediaAndThreadVideos() {
        var media = 0
        var videos = 0
        var threads = 0
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = listOf(ROW.copy(imageSpoiler = true)),
                    mode = mode,
                    thumbnail = { _, modifier -> Box(modifier) },
                    onOpenRow = { threads++ },
                    onOpenMedia = { media++ },
                    onOpenReplies = { videos++ },
                )
            }
        }
        openWithTouch()
        compose.onNodeWithText("Open media in ${ROW.subject}").assertDoesNotExist()
        compose.onNodeWithText("View videos in ${ROW.subject}").assertDoesNotExist()
        compose.onNodeWithText("Reveal spoiler").performClick()
        compose.runOnIdle {
            assertEquals(0, media)
            assertEquals(0, videos)
            assertEquals(0, threads)
        }
        openWithTouch()
        compose.onNodeWithText("Open media in ${ROW.subject}").performClick()
        openWithTouch()
        compose.onNodeWithText("View videos in ${ROW.subject}").performClick()
        compose.runOnIdle {
            assertEquals(1, media)
            assertEquals(1, videos)
            assertEquals(0, threads)
        }
    }

    @Test
    fun unwiredOptionsProvideNoEmptyMenuOrLongClickAction() {
        var opened = 0
        compose.setContent {
            NextTheme {
                FeedScreen(rows = listOf(ROW), mode = mode, onOpenRow = { opened++ })
            }
        }
        val row = compose.onNodeWithTag(ReaderFeedTags.row(ROW.id))
        val semantics = row.fetchSemanticsNode().config
        assertFalse(semantics.contains(SemanticsActions.OnLongClick))
        assertFalse(semantics.contains(SemanticsActions.CustomActions))
        compose.onNodeWithTag(ReaderFeedTags.more(ROW.id)).assertDoesNotExist()
        row.performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun holdingThumbnailOrSpoilerOpensOptionsWithoutOpeningOrRevealingMedia() {
        var covered by mutableStateOf(false)
        var media = 0
        var threads = 0
        var watches = 0
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = listOf(ROW.copy(imageSpoiler = covered)),
                    mode = mode,
                    thumbnail = { _, modifier -> Box(modifier) },
                    onOpenRow = { threads++ },
                    onOpenMedia = { media++ },
                    onToggleWatch = { watches++ },
                )
            }
        }
        val thumbnail = compose.onNodeWithTag(ReaderFeedTags.thumbnail(ROW.id), useUnmergedTree = true)
        thumbnail.performTouchInput { longClick() }
        compose.onNodeWithText("Open media in ${ROW.subject}").assertIsDisplayed()
        compose.onNodeWithText("Watch ${ROW.subject}").performClick()
        compose.runOnIdle {
            assertEquals(1, watches)
            assertEquals(0, media)
            assertEquals(0, threads)
        }
        thumbnail.performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, media)
            covered = true
        }
        thumbnail.performTouchInput { longClick() }
        compose.onNodeWithText("Reveal spoiler").assertIsDisplayed()
        compose.onNodeWithText("Open media in ${ROW.subject}").assertDoesNotExist()
        compose.onNodeWithText("Watch ${ROW.subject}").performClick()
        compose.runOnIdle {
            assertEquals(2, watches)
            assertEquals(1, media)
            assertEquals(0, threads)
        }
        thumbnail.performTouchInput { click() }
        compose.runOnIdle { assertEquals("A normal first tap must still only reveal the spoiler", 1, media) }
        thumbnail.performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, media) }
    }

    @Test
    fun removingTheLastActionDismissesItsMenuAndRestoringItRequiresAnotherLongPress() {
        var enabled by mutableStateOf(true)
        var watched = 0
        var threads = 0
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = listOf(ROW),
                    mode = mode,
                    onOpenRow = { threads++ },
                    onToggleWatch = if (enabled) ({ watched++ }) else null,
                )
            }
        }
        openWithTouch()
        compose.onNodeWithText("Watch ${ROW.subject}").assertIsDisplayed()
        compose.runOnIdle { enabled = false }
        compose.onNode(isPopup()).assertDoesNotExist()
        assertFalse(
            compose
                .onNodeWithTag(ReaderFeedTags.row(ROW.id))
                .fetchSemanticsNode()
                .config
                .contains(SemanticsActions.OnLongClick),
        )
        compose.runOnIdle { enabled = true }
        compose.onNode(isPopup()).assertDoesNotExist()
        compose.onNodeWithText("Watch ${ROW.subject}").assertDoesNotExist()
        openWithTouch()
        compose.onNodeWithText("Watch ${ROW.subject}").performClick()
        compose.runOnIdle {
            assertEquals(1, watched)
            assertEquals(0, threads)
        }
    }

    private fun openWithTouch() {
        compose.onNodeWithTag(ReaderFeedTags.row(ROW.id)).performTouchInput {
            // Press the row/card itself rather than its independent thumbnail media control.
            longClick(Offset(width - 8f, height - 8f))
        }
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun modes(): List<Array<CatalogMode>> = CatalogMode.entries.map { arrayOf(it) }

        private val ROW =
            FeedRow(
                id = "fourchan/g/7",
                subject = "Long press fixture",
                board = "/g/",
                site = "4chan",
                activity = "1m",
                replies = 5,
                media = 2,
            )
    }
}
