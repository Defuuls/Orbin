package com.orbin.uinext.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.orbin.uinext.components.NextTheme
import com.orbin.uinext.components.ReaderAppBarTags
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThreadFeatureInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun hiddenPostDoesNotComposeItsTextOrMediaOrBecomeSeenBeforeAnExplicitReveal() {
        var revealed by mutableStateOf(false)
        val seen = mutableSetOf<String>()
        compose.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "Thread",
                    board = "/g/",
                    posts =
                        listOf(
                            Post("No.1", "", "Opening", id = "1"),
                            Post(
                                "No.2",
                                "",
                                "Private reply",
                                id = "2",
                                hasMedia = true,
                                hiddenReason = if (revealed) null else "Hidden by you",
                            ),
                        ),
                    onRevealPost = { revealed = true },
                    onVisiblePosts = { seen += it },
                    media = { _, _ -> Text("Private attachment") },
                )
            }
        }
        compose.onNodeWithText("Private reply").assertDoesNotExist()
        compose.onNodeWithText("Private attachment").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertFalse("2" in seen) }
        compose.onNodeWithText("Reveal post").performClick()
        compose.onNodeWithText("Private reply").assertExists()
        compose.onNodeWithText("Private attachment").assertExists()
    }

    @Test
    fun posterDetailSurvivesTheSaveableStateCycleUsedByGalleryNavigation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "Thread",
                    board = "/g/",
                    posts =
                        listOf(
                            Post("No.1", "", "Opening", id = "1"),
                            Post("No.2", "", "Alice reply", id = "2", posterId = "abc"),
                            Post("No.3", "", "Other poster", id = "3", posterId = "xyz"),
                        ),
                )
            }
        }
        compose.onNodeWithText("ID:abc").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(ReaderAppBarTags.TITLE, useUnmergedTree = true).assertTextEquals("ID:abc")
        compose.onNodeWithText("Alice reply").assertExists()
        compose.onNodeWithText("Other poster").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Opening").assertExists()
    }

    @Test
    fun posterIdDetailsShowOnlyThatPosterAndBackRestoresTheHiddenRootPresentation() {
        compose.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "Thread",
                    board = "/g/",
                    posts =
                        listOf(
                            Post("No.1", "", "Opening", id = "1"),
                            Post(
                                "No.2",
                                "",
                                "Hidden Alice reply",
                                id = "2",
                                posterId = "abc",
                                posterName = "Alice",
                                hiddenReason = "Hidden by you",
                            ),
                            Post("No.3", "", "Other poster", id = "3", posterId = "xyz"),
                            Post("No.4", "", "Visible Alice reply", id = "4", posterId = "abc", posterName = "Alice"),
                        ),
                )
            }
        }
        compose.onNodeWithText("Hidden Alice reply").assertDoesNotExist()
        compose.onAllNodesWithText("ID:abc")[0].performClick()
        compose.onNodeWithTag(ReaderAppBarTags.TITLE, useUnmergedTree = true).assertTextEquals("ID:abc")
        compose.onNodeWithText("Hidden Alice reply").assertExists()
        compose.onNodeWithText("Visible Alice reply").assertExists()
        compose.onNodeWithText("Other poster").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag(ReaderAppBarTags.TITLE, useUnmergedTree = true).assertTextEquals("Thread")
        compose.onNodeWithText("Hidden Alice reply").assertDoesNotExist()
        compose.onNodeWithText("Other poster").assertExists()
    }
}
