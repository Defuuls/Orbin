package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Clover post and album geometry, media slots, responsive text and visible metadata. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ReaderThreadPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a post's thumbnail is the specified square and holds the platform's media`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "Portrait images",
                    board = "/wg/",
                    posts = listOf(Post(number = "No. 1", time = "2m", body = "A portrait", hasMedia = true)),
                    media = { _, modifier -> Box(modifier.testTag("platform-media")) },
                )
            }
        }
        composeRule
            .onNodeWithTag(ThreadTags.THUMBNAIL, useUnmergedTree = true)
            .assertWidthIsEqualTo(ReaderSize.thumbnail)
            .assertHeightIsEqualTo(ReaderSize.thumbnail)
        composeRule
            .onNodeWithTag("platform-media", useUnmergedTree = true)
            .assertWidthIsEqualTo(ReaderSize.thumbnail)
            .assertHeightIsEqualTo(ReaderSize.thumbnail)
    }

    @Test
    fun `a wide window uses the larger thumbnail`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts = listOf(Post(number = "No. 1", time = "2m", body = "A post", hasMedia = true)),
                    wideWindow = true,
                )
            }
        }
        composeRule
            .onNodeWithTag(ThreadTags.THUMBNAIL, useUnmergedTree = true)
            .assertWidthIsEqualTo(ReaderSize.thumbnailWide)
            .assertHeightIsEqualTo(ReaderSize.thumbnailWide)
    }

    @Test
    fun `a post with several files shows how many more there are and one with a single file does not`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts =
                        listOf(
                            Post(number = "No. 1", time = "2m", body = "Three", hasMedia = true, mediaCount = 3),
                            Post(number = "No. 2", time = "1m", body = "One", hasMedia = true),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("+2", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("+0", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `files use three Clover album cells with one dp margins and a metadata strip`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts = ONE_POST,
                    layout = ThreadLayout.FILES,
                    files =
                        (1..4).map {
                            MediaCell(id = "file-$it", board = "/g/", details = "JPG 640x480 2 KB")
                        },
                )
            }
        }
        val tiles = composeRule.onAllNodesWithTag(ThreadTags.FILE)
        val first = tiles[0].getUnclippedBoundsInRoot()
        val third = tiles[2].getUnclippedBoundsInRoot()
        val fourth = tiles[3].getUnclippedBoundsInRoot()
        assertEquals(first.top.value, third.top.value, 0.5f)
        assertTrue("the fourth file starts a new row", fourth.top > first.top)
        assertEquals((ReaderSpace.albumPadding + ReaderSpace.albumCellMargin).value, first.left.value, 0.5f)
        // A short last row keeps the tile size of the full rows above it.
        assertEquals((first.right - first.left).value, (fourth.right - fourth.left).value, 0.5f)
        assertEquals(
            (first.right - first.left + ReaderSize.albumDetails).value,
            (first.bottom - first.top).value,
            0.5f,
        )
        composeRule.onAllNodesWithTag(ThreadTags.FILE_DETAILS).assertCountEquals(4)
        composeRule.onAllNodesWithTag(ThreadTags.FILE_DETAILS)[0].assertHeightIsEqualTo(ReaderSize.albumDetails)
    }

    @Test
    fun `post text uses Clover inset without media and after the leading thumbnail with it`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts =
                        listOf(
                            Post(number = "No. 1", time = "2m", body = "Original post", hasMedia = true),
                            Post(number = "No. 2", time = "1m", body = "A new project", author = "skywatcher"),
                        ),
                )
            }
        }
        val author = composeRule.onNodeWithText("skywatcher", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val body = composeRule.onNodeWithText("A new project", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val withMedia = composeRule.onNodeWithText("Original post", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(author.left.value, body.left.value, 0.5f)
        assertEquals(ReaderSpace.postInset.value, body.left.value, 0.5f)
        assertEquals(
            (ReaderSize.thumbnail + ReaderSpace.postInset).value,
            withMedia.left.value,
            0.5f,
        )
        val thumbnail = composeRule.onNodeWithTag(ThreadTags.THUMBNAIL, true).getUnclippedBoundsInRoot()
        assertEquals(0f, thumbnail.left.value, 0.5f)
    }

    @Test
    fun `a reply is indented one gap per level and no further than three levels`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts =
                        listOf(
                            Post(number = "No. 1", time = "2m", body = "Level zero"),
                            Post(number = "No. 2", time = "2m", body = "Level one", depth = 1),
                            Post(number = "No. 3", time = "2m", body = "Level three", depth = 3),
                            Post(number = "No. 4", time = "2m", body = "Level nine", depth = 9),
                        ),
                )
            }
        }
        val left = { text: String ->
            composeRule
                .onNodeWithText(text, useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
                .left.value
        }
        assertEquals(ReaderSpace.postInset.value, left("Level zero"), 0.5f)
        assertEquals((ReaderSpace.postInset + ReaderSpace.gap).value, left("Level one"), 0.5f)
        assertEquals((ReaderSpace.postInset + ReaderSpace.gap * 3).value, left("Level three"), 0.5f)
        assertEquals(left("Level three"), left("Level nine"), 0.5f)
    }

    @Test
    fun `the footer counts replies and files`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts =
                        listOf(
                            Post(number = "No. 1", time = "2m", body = "One", hasMedia = true, mediaCount = 2),
                            Post(number = "No. 2", time = "1m", body = "Two"),
                            Post(number = "No. 3", time = "1m", body = "Three", hasMedia = true),
                        ),
                )
            }
        }
        composeRule.onNodeWithTag(ThreadTags.STATUS).assertHeightIsEqualTo(ReaderSize.threadStatus)
        composeRule.onNodeWithText("2 replies · 3 files").assertExists()
    }

    @Test
    fun `at font two a post puts the square thumbnail above its body`() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                NextTheme {
                    ThreadScreen(
                        subject = "A thread",
                        board = "/g/",
                        posts = listOf(Post(number = "1", time = "2m", body = "Large text", hasMedia = true)),
                    )
                }
            }
        }
        val thumbnail = composeRule.onNodeWithTag(ThreadTags.THUMBNAIL, true).getUnclippedBoundsInRoot()
        val body = composeRule.onNodeWithText("Large text", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(ReaderSize.thumbnail.value, (thumbnail.right - thumbnail.left).value, 0.5f)
        assertEquals((thumbnail.right - thumbnail.left).value, (thumbnail.bottom - thumbnail.top).value, 0.5f)
        assertTrue(body.top > thumbnail.bottom)
        assertEquals(ReaderSpace.postInset.value, body.left.value, 0.5f)
    }

    private companion object {
        val ONE_POST = listOf(Post(number = "No. 1", time = "2m", body = "A quiet lake"))
    }
}
