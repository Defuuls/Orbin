package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The thread screen of docs/design-system: app bar actions, the shared post cell, and Files. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SocialThreadTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `thread source stays visible after scrolling to a reply`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "Weekly discussion",
                    siteName = "bbwchan",
                    board = "/gen/",
                    posts = (1..30).map { Post(number = "No. $it", time = "2m", body = "Post $it") },
                )
            }
        }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(20)
        composeRule.onNodeWithText("BBWChan · /gen/").assertIsDisplayed()
        composeRule.onNodeWithText("Weekly discussion").assertIsDisplayed()
    }

    @Test
    fun `back, reload and watch are app bar buttons and share is in the overflow`() {
        var backs = 0
        var shares = 0
        var watches = 0
        var reloads = 0
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "Landscapes",
                    siteName = "fourchan",
                    board = "/wg/",
                    posts = listOf(Post(number = "No. 1", time = "2m", body = "A quiet lake")),
                    onBack = { backs++ },
                    onShare = { shares++ },
                    onWatch = { watches++ },
                    onReload = { reloads++ },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithContentDescription("Reload").performClick()
        composeRule.onNodeWithContentDescription("Watch").performClick()
        composeRule.onNodeWithText("Share thread").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Share thread").performClick()
        assertEquals(1, backs)
        assertEquals(1, reloads)
        assertEquals(1, watches)
        assertEquals(1, shares)
    }

    @Test
    fun `a thread with no way back and no reload draws neither button`() {
        composeRule.setContent { NextTheme { ThreadScreen(subject = "A thread", board = "/g/", posts = ONE_POST) } }

        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Reload").assertDoesNotExist()
    }

    @Test
    fun `collapsing an anonymous post removes its body without losing its identity`() {
        val post = Post(number = "No. 42", time = "3m", body = "A small project")
        composeRule.setContent {
            var collapsed by remember { mutableStateOf(emptySet<String>()) }
            NextTheme {
                ThreadScreen(
                    subject = "Weekly discussion",
                    board = "/gen/",
                    posts = listOf(post),
                    collapsed = collapsed,
                    onToggleCollapse = { collapsed = if (collapsed.isEmpty()) setOf(it.id) else emptySet() },
                )
            }
        }
        composeRule.onNodeWithText("Anonymous").performClick()
        composeRule.onNodeWithText("A small project").assertDoesNotExist()
        composeRule.onNodeWithText("No. 42").assertExists()
        composeRule.onNodeWithText("3m").assertExists()
        composeRule.onNodeWithText("Anonymous").performClick()
        composeRule.onNodeWithText("A small project").assertIsDisplayed()
    }

    @Test
    fun `a collapsed post hides its thumbnail too`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts = listOf(Post(number = "No. 1", time = "2m", body = "A post", hasMedia = true, id = "1")),
                    collapsed = setOf("1"),
                )
            }
        }
        composeRule.onAllNodesWithTag(ThreadTags.THUMBNAIL, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `saving is offered from the overflow and does not watch`() {
        var saves = 0
        var watches = 0
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "Landscapes",
                    board = "/wg/",
                    posts = ONE_POST,
                    onToggleSaved = { saves++ },
                    onWatch = { watches++ },
                )
            }
        }
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Save Landscapes").performClick()
        assertEquals(1, saves)
        assertEquals(0, watches)
    }

    @Test
    fun `a thread that cannot be saved offers no save entry`() {
        composeRule.setContent { NextTheme { ThreadScreen(subject = "Landscapes", board = "/wg/", posts = ONE_POST) } }

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Save Landscapes").assertDoesNotExist()
    }

    @Test
    fun `a watched thread explains why it stays saved without offering a control that does nothing`() {
        var saves = 0
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "Landscapes",
                    board = "/wg/",
                    posts = ONE_POST,
                    watching = true,
                    saved = true,
                    onToggleSaved = { saves++ },
                )
            }
        }
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule
            .onNodeWithText("Saved while watching. Turn off Watch to remove Landscapes.")
            .assertIsNotEnabled()
        composeRule.onNodeWithText("Remove Landscapes from saved threads").assertDoesNotExist()
        assertEquals(0, saves)
    }

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
    fun `files and posts switch from the overflow`() {
        composeRule.setContent {
            var layout by remember { mutableStateOf(ThreadLayout.POSTS) }
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts = listOf(Post(number = "No. 1", time = "2m", body = "A post")),
                    layout = layout,
                    onLayoutChange = { layout = it },
                    files = listOf(MediaCell(id = "file-1", board = "/g/")),
                )
            }
        }
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Posts").assertDoesNotExist()
        composeRule.onNodeWithText("Download all").assertDoesNotExist()
        composeRule.onNodeWithText("Files").performClick()
        composeRule.onNodeWithText("A post").assertDoesNotExist()
        composeRule.onAllNodesWithTag(ThreadTags.FILE).assertCountEquals(1)

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Files").assertDoesNotExist()
        composeRule.onNodeWithText("Download all").assertExists()
        composeRule.onNodeWithText("Posts").performClick()
        composeRule.onNodeWithText("A post").assertIsDisplayed()
    }

    @Test
    fun `files are laid out three across in a 411dp pane with a gap around each`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts = ONE_POST,
                    layout = ThreadLayout.FILES,
                    files = (1..4).map { MediaCell(id = "file-$it", board = "/g/") },
                )
            }
        }
        val tiles = composeRule.onAllNodesWithTag(ThreadTags.FILE)
        val first = tiles[0].getUnclippedBoundsInRoot()
        val third = tiles[2].getUnclippedBoundsInRoot()
        val fourth = tiles[3].getUnclippedBoundsInRoot()
        assertEquals(first.top.value, third.top.value, 0.5f)
        assertTrue("the fourth file starts a new row", fourth.top > first.top)
        assertEquals(ReaderSpace.gridGap.value, first.left.value, 0.5f)
        // A short last row keeps the tile size of the full rows above it.
        assertEquals((first.right - first.left).value, (fourth.right - fourth.left).value, 0.5f)
        assertEquals((first.right - first.left).value, (first.bottom - first.top).value, 0.5f)
    }

    @Test
    fun `post text starts at the gutter without media and after the thumbnail with it`() {
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
        assertEquals(ReaderSpace.gutter.value, body.left.value, 0.5f)
        assertEquals(
            (ReaderSpace.gutter + ReaderSize.thumbnail + ReaderSpace.gap).value,
            withMedia.left.value,
            0.5f,
        )
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
        assertEquals(ReaderSpace.gutter.value, left("Level zero"), 0.5f)
        assertEquals((ReaderSpace.gutter + ReaderSpace.gap).value, left("Level one"), 0.5f)
        assertEquals((ReaderSpace.gutter + ReaderSpace.gap * 3).value, left("Level three"), 0.5f)
        assertEquals(left("Level three"), left("Level nine"), 0.5f)
    }

    @Test
    fun `replies to a post are links that report the post they name`() {
        val followed = mutableListOf<String>()
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts =
                        listOf(
                            Post(
                                number = "No. 1",
                                time = "2m",
                                body = "A post",
                                replies = 2,
                                replyNumbers = listOf("7", "9"),
                            ),
                        ),
                    onPostReference = { followed += it },
                )
            }
        }
        composeRule.onNodeWithText(">>9").assertHeightIsAtLeast(ReaderSize.touchTarget).performClick()
        composeRule.onNodeWithText("2 replies").assertDoesNotExist()
        assertEquals(listOf("9"), followed)
    }

    @Test
    fun `without reply numbers a post shows its reply count and a post with none shows nothing`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts =
                        listOf(
                            Post(number = "No. 1", time = "2m", body = "Answered", replies = 2),
                            Post(number = "No. 2", time = "1m", body = "Unanswered"),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("2 replies", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("0 replies", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the first unseen post is marked and can be jumped to from the overflow`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A long thread",
                    board = "/g/",
                    posts = (1..30).map { Post(number = "No. $it", time = "2m", body = "Post $it", id = "$it") },
                    firstUnreadPostId = "25",
                )
            }
        }
        composeRule.onAllNodesWithTag(ThreadTags.UNREAD).assertCountEquals(0)
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("First new post").performClick()
        composeRule.onNodeWithText("Post 25").assertIsDisplayed()
        composeRule.onNodeWithTag(ThreadTags.UNREAD, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `an unseen opening post carries no marker`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts = listOf(Post(number = "No. 1", time = "2m", body = "A post", id = "1")),
                    firstUnreadPostId = "1",
                )
            }
        }
        composeRule.onAllNodesWithTag(ThreadTags.UNREAD, useUnmergedTree = true).assertCountEquals(0)
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
    fun `a spoiler post shows its text only after it is revealed`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts = listOf(Post(number = "No. 1", time = "2m", body = "The ending", spoiler = true)),
                )
            }
        }
        composeRule.onNodeWithText("The ending", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Spoiler — press to reveal", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("The ending", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `opening files preserves the exact posts reading position`() {
        var layout by mutableStateOf(ThreadLayout.POSTS)
        lateinit var postsState: LazyListState
        composeRule.setContent {
            postsState = rememberLazyListState()
            NextTheme {
                ThreadScreen(
                    subject = "A long thread",
                    board = "/g/",
                    posts = (1..40).map { Post(number = "No. $it", time = "2m", body = "Post $it") },
                    listState = postsState,
                    layout = layout,
                    files = (1..60).map { MediaCell(id = "file-$it", board = "/g/") },
                )
            }
        }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(20)
        var index = 0
        var offset = 0
        composeRule.runOnIdle {
            index = postsState.firstVisibleItemIndex
            offset = postsState.firstVisibleItemScrollOffset
            layout = ThreadLayout.FILES
        }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(8)
        composeRule.runOnIdle { layout = ThreadLayout.POSTS }
        composeRule.runOnIdle {
            assertEquals(index, postsState.firstVisibleItemIndex)
            assertEquals(offset, postsState.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun `the post at index n is list item n plus one, as hosts restoring a position assume`() {
        lateinit var postsState: LazyListState
        composeRule.setContent {
            postsState = rememberLazyListState()
            NextTheme {
                ThreadScreen(
                    subject = "A long thread",
                    board = "/g/",
                    posts = (1..40).map { Post(number = "No. $it", time = "2m", body = "Post $it") },
                    listState = postsState,
                )
            }
        }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(11)
        composeRule.onNodeWithText("Post 11", useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(11, postsState.firstVisibleItemIndex) }
    }

    @Test
    fun `jumping to a collapsed quoted post expands its content before scrolling`() {
        var collapsed by mutableStateOf(setOf("20"))
        var target by mutableStateOf<String?>(null)
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A long thread",
                    board = "/g/",
                    posts = (1..30).map { Post(number = "No. $it", time = "2m", body = "Post $it", id = "$it") },
                    collapsed = collapsed,
                    onToggleCollapse = { collapsed = collapsed - it.id },
                    scrollToPostId = target,
                    onScrollConsumed = { target = null },
                )
            }
        }
        composeRule.runOnIdle { target = "20" }
        composeRule.onNodeWithText("Post 20").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue("20" !in collapsed) }
    }

    @Test
    fun `source labels distinguish provider ids without changing custom sources`() {
        assertEquals("4chan", sourceLabel("fourchan"))
        assertEquals("BBWChan", sourceLabel("BBW Chan"))
        assertEquals("A custom board", sourceLabel(" A custom board "))
    }

    private companion object {
        val ONE_POST = listOf(Post(number = "No. 1", time = "2m", body = "A quiet lake"))
    }
}
