package com.orbin.uinext

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ThreadChatActionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rowMenuOpensVideosWithoutOpeningTheThreadDestination() {
        var captions = 0
        var videos = 0
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = listOf(row()),
                    onOpenRow = { captions++ },
                    onOpenReplies = { videos++ },
                    repliesOpenComments = false,
                )
            }
        }

        compose.onNodeWithTag(ReaderFeedTags.more(row().id)).performClick()
        compose.onNodeWithText("View videos in Original artwork").performClick()
        compose.runOnIdle {
            assertEquals(1, videos)
            assertEquals(0, captions)
        }
        compose.onNodeWithTag(ReaderFeedTags.row(row().id)).performClick()
        compose.runOnIdle {
            assertEquals(1, videos)
            assertEquals(1, captions)
        }
    }

    @Test
    fun enabledCommentsAnnounceAndOpenReplies() {
        var comments = 0
        compose.setContent {
            NextTheme {
                FeedScreen(
                    rows = listOf(row()),
                    onOpenReplies = { comments++ },
                    repliesOpenComments = true,
                )
            }
        }

        compose.onNodeWithTag(ReaderFeedTags.more(row().id)).performClick()
        compose.onNodeWithText("View replies to Original artwork").performClick()
        compose.runOnIdle { assertEquals(1, comments) }
    }

    private fun row() =
        FeedRow(
            subject = "Original artwork",
            board = "/p/",
            activity = "now",
            replies = 5,
            media = 3,
            id = "fourchan/p/4",
        )
}
