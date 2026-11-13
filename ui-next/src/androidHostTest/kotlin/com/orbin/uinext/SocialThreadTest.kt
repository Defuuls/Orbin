package com.orbin.uinext

import androidx.compose.foundation.clickable
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
    fun `back album and watch are app bar buttons while reload and share are in the overflow`() {
        var backs = 0
        var shares = 0
        var watches = 0
        var reloads = 0
        var requestedLayout: ThreadLayout? = null
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
                    onLayoutChange = { requestedLayout = it },
                )
            }
        }
        val album = composeRule.onNodeWithContentDescription("Files").getUnclippedBoundsInRoot()
        val watch = composeRule.onNodeWithContentDescription("Watch").getUnclippedBoundsInRoot()
        assertTrue("Clover's album action precedes the bookmark action", album.left < watch.left)
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithContentDescription("Reload").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Files").performClick()
        composeRule.onNodeWithContentDescription("Watch").performClick()
        composeRule.onNodeWithText("Share thread").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Reload").performClick()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Share thread").performClick()
        assertEquals(1, backs)
        assertEquals(1, reloads)
        assertEquals(1, watches)
        assertEquals(1, shares)
        assertEquals(ThreadLayout.FILES, requestedLayout)
    }

    @Test
    fun `a thread with no way back and no reload draws neither button`() {
        composeRule.setContent { NextTheme { ThreadScreen(subject = "A thread", board = "/g/", posts = ONE_POST) } }

        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Reload").assertDoesNotExist()
    }

    @Test
    fun `collapsing an anonymous post removes its body without losing its identity`() {
        val post = Post(number = "No. 42", time = "3m", body = "A small project", subject = "Opening subject")
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
        composeRule.onNodeWithText("Anonymous").assertDoesNotExist()
        composeRule.onNodeWithText("Opening subject", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("No. 42").performClick()
        composeRule.onNodeWithText("A small project").assertDoesNotExist()
        composeRule.onNodeWithText("No. 42").assertExists()
        composeRule.onNodeWithText("3m").assertExists()
        composeRule.onNodeWithText("Opening subject", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("No. 42").performClick()
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
    fun `a thread without action callbacks offers no save watch share files or download controls`() {
        composeRule.setContent { NextTheme { ThreadScreen(subject = "Landscapes", board = "/wg/", posts = ONE_POST) } }

        composeRule.onNodeWithContentDescription("More options").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Watch").assertDoesNotExist()
        composeRule.onNodeWithText("Save Landscapes").assertDoesNotExist()
        composeRule.onNodeWithText("Share thread").assertDoesNotExist()
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
    fun `files and posts switch from the album action while file downloads stay in overflow`() {
        composeRule.setContent {
            var layout by remember { mutableStateOf(ThreadLayout.POSTS) }
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts = listOf(Post(number = "No. 1", time = "2m", body = "A post")),
                    layout = layout,
                    onLayoutChange = { layout = it },
                    onDownloadAll = {},
                    files = listOf(MediaCell(id = "file-1", board = "/g/")),
                )
            }
        }
        composeRule.onNodeWithContentDescription("Posts").assertDoesNotExist()
        composeRule.onNodeWithText("Download all").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Files").performClick()
        composeRule.onNodeWithText("A post").assertDoesNotExist()
        composeRule.onAllNodesWithTag(ThreadTags.FILE).assertCountEquals(1)

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithContentDescription("Files").assertDoesNotExist()
        composeRule.onNodeWithText("Download all").assertExists()
        composeRule.onNodeWithText("Download all").performClick()
        composeRule.onNodeWithContentDescription("Posts").performClick()
        composeRule.onNodeWithText("A post").assertIsDisplayed()
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
    fun `the first unseen post is marked and can be jumped to from the new pill`() {
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
        composeRule.onNodeWithTag(ThreadTags.NEW_PILL).assertExists()
        composeRule.onNodeWithText("6 new").performClick()
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
    fun `the image spoiler first tap reveals without opening the viewer`() {
        var opens = 0
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts =
                        listOf(
                            Post(number = "1", time = "2m", body = "Body", hasMedia = true, imageSpoiler = true),
                        ),
                    media = { _, modifier -> Box(modifier.testTag("media-viewer").clickable { opens++ }) },
                )
            }
        }
        composeRule.onNodeWithTag("media-viewer", true).assertDoesNotExist()
        composeRule.onNodeWithText("Spoiler").performClick()
        assertEquals(0, opens)
        composeRule.onNodeWithTag("media-viewer").performClick()
        assertEquals(1, opens)
    }

    @Test
    fun `a Files spoiler first tap reveals its preview without opening the viewer`() {
        var opens = 0
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts = emptyList(),
                    layout = ThreadLayout.FILES,
                    files = listOf(MediaCell("file", "/g/", spoiler = true)),
                    onOpenFile = { opens++ },
                    fileTile = { _, modifier -> Box(modifier.testTag("file-preview")) },
                )
            }
        }
        composeRule.onNodeWithTag("file-preview", true).assertDoesNotExist()
        composeRule.onNodeWithText("Spoiler").performClick()
        assertEquals(0, opens)
        composeRule.onNodeWithTag("file-preview", true).assertExists()
        composeRule.onNodeWithTag(ThreadTags.FILE).performClick()
        assertEquals(1, opens)
    }

    @Test
    fun `reply numbers have no link action when the host cannot follow references`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts =
                        listOf(
                            Post(
                                number = "1",
                                time = "2m",
                                body = "Body",
                                replies = 2,
                                replyNumbers = listOf("7", "9"),
                            ),
                        ),
                )
            }
        }
        composeRule.onNodeWithText(">>9").assertDoesNotExist()
        composeRule.onNodeWithText("2 replies", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `refresh preserves posts under a progress bar and footer uses the loaded timestamp`() {
        composeRule.setContent {
            NextTheme {
                ThreadScreen(
                    subject = "A thread",
                    board = "/g/",
                    posts = ONE_POST,
                    reloading = true,
                    statusText = "0 replies · 0 images · Updated 10:42",
                )
            }
        }
        composeRule.onNodeWithTag(ThreadTags.PROGRESS).assertHeightIsEqualTo(ReaderSize.progress)
        composeRule.onNodeWithText("A quiet lake").assertIsDisplayed()
        composeRule.onNodeWithText("0 replies · 0 images · Updated 10:42").assertExists()
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
