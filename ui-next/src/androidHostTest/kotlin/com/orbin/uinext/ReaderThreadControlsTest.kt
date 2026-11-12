package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.orbin.uinext.tokens.ReaderSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Thread-end navigation and semantic media controls exercise their actual production handlers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ReaderThreadControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Bottom reaches the entire footer after a last post taller than the viewport`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A long final post",
                    board = "/g/",
                    posts =
                        listOf(
                            Post(number = "1", time = "2m", body = "Opening post"),
                            Post(
                                number = "2",
                                time = "1m",
                                body = (1..80).joinToString("\n") { "Final post line $it" },
                            ),
                        ),
                )
            }
        }
        val viewport = composeRule.onNodeWithTag(ThreadTags.LIST).getUnclippedBoundsInRoot()
        val lastBody =
            composeRule
                .onNodeWithText("Final post line 1", substring = true, useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        assertTrue(
            "The final body must be taller than the reader viewport",
            lastBody.bottom - lastBody.top > viewport.bottom - viewport.top,
        )
        composeRule.onNodeWithTag(ThreadTags.STATUS).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Bottom").performClick()

        val footer =
            composeRule
                .onNodeWithTag(ThreadTags.STATUS)
                .assertIsDisplayed()
                .assertHeightIsEqualTo(ReaderSize.threadStatus)
                .getUnclippedBoundsInRoot()
        assertTrue("The footer must be fully inside the viewport: $footer in $viewport", footer.top >= viewport.top)
        assertTrue("Bottom must reach the footer's end: $footer in $viewport", footer.bottom <= viewport.bottom)
    }

    @Test
    fun `a covered Files tile semantic action reveals before it can open the viewer`() {
        var opens = 0
        var compositions = 0
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts = emptyList(),
                    layout = ThreadLayout.FILES,
                    files =
                        listOf(MediaCell("site/g/1/file/0", "/g/", spoiler = true, details = "WEBM 1920x1080 2 MB")),
                    onOpenFile = { opens++ },
                    fileTile = { _, modifier ->
                        SideEffect { compositions++ }
                        Box(modifier.testTag("semantic-file-preview"))
                    },
                )
            }
        }
        val tile = composeRule.onNodeWithTag(ThreadTags.FILE)
        assertEquals("Reveal spoiler", tile.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        composeRule.onNodeWithTag("semantic-file-preview", true).assertDoesNotExist()
        composeRule.onNodeWithText("WEBM 1920x1080 2 MB", useUnmergedTree = true).assertExists()
        composeRule.runOnIdle { assertEquals(0, compositions) }
        tile.performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(0, opens)
            assertTrue(compositions > 0)
        }
        composeRule.onNodeWithTag("semantic-file-preview", true).assertExists()
        tile.performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        composeRule.runOnIdle { assertEquals(1, opens) }
    }
}
