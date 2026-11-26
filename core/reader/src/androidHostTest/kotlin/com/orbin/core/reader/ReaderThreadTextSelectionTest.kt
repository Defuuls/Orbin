package com.orbin.core.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Magnifier
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionGroup
import androidx.compose.runtime.tooling.LocalInspectionTables
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.test.core.app.ApplicationProvider
import com.orbin.core.model.BoardId
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import com.orbin.core.model.PosterInfo
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.uinext.components.NextTheme
import com.orbin.uinext.screens.ThreadScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * The thread list shares one selection container between its post bodies instead of building one
 * per post, while long presses keep their meaning: on a comment they select its text, anywhere
 * else in the post they open the post menu.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w390dp-h844dp-mdpi", shadows = [SurfacelessMagnifier::class])
class ReaderThreadTextSelectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val tables = mutableSetOf<CompositionData>()
    private lateinit var listState: LazyListState
    private val toolbar = RecordingTextToolbar()

    private fun setThread(
        replies: Int,
        menu: Boolean = true,
    ) {
        val presentation = runBlocking { thread(replies).preparePresentation(nowMillis = CREATED_AT) }
        compose.setContent {
            currentComposer.collectParameterInformation()
            CompositionLocalProvider(LocalInspectionTables provides tables, LocalTextToolbar provides toolbar) {
                listState = rememberLazyListState()
                NextTheme {
                    ThreadScreen(
                        subject = "Selection",
                        board = "/g/",
                        siteName = "fourchan",
                        posts = presentation.rowModels,
                        listState = listState,
                        body = { post -> presentation.rowsById[post.id]?.let { ReaderPostComment(it) } },
                        onHidePost = if (menu) ({ _ -> }) else null,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** Selection containers currently composed anywhere in the screen, lazy rows included. */
    private fun selectionContainers(): Int {
        compose.waitForIdle()
        return tables.toList().sumOf { data -> data.compositionGroups.sumOf(::countContainers) }
    }

    private fun countContainers(group: CompositionGroup): Int =
        (if (group.sourceInfo?.startsWith(CONTAINER_SOURCE) == true) 1 else 0) +
            group.compositionGroups.sumOf(::countContainers)

    @Test
    fun postBodiesShareOneSelectionContainerWhileScrolling() {
        setThread(replies = POSTS)
        val counts = mutableListOf(selectionContainers())
        repeat(SCROLL_STEPS) {
            compose.runOnIdle { listState.dispatchRawDelta(SCROLL_STEP_PX) }
            counts += selectionContainers()
        }
        println("ReaderThreadTextSelectionTest containers per frame: $counts")
        assertTrue("Composition data must be collected for this count to mean anything", tables.isNotEmpty())
        // The public SelectionContainer delegates to an inner overload of the same name.
        assertEquals("One shared container (two groups) whatever is on screen", List(counts.size) { 2 }, counts)
    }

    @Test
    fun aLongPressOnACommentSelectsItsText() {
        setThread(replies = 3, menu = false)
        compose.onAllNodes(isPopup()).assertCountEquals(0)
        compose
            .onNodeWithText("Reply text 2", substring = true, useUnmergedTree = true)
            .performTouchInput { longClick() }
        compose.waitForIdle()
        // The selection's two handles.
        compose.onAllNodes(isPopup()).assertCountEquals(2)
    }

    @Test
    fun theRestOfAPostStaysUnselectable() {
        setThread(replies = 3, menu = false)
        compose.onNodeWithText("No. 102", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onAllNodes(isPopup()).assertCountEquals(0)
    }

    @Test
    fun aLongPressOutsideTheCommentOpensThePostMenu() {
        setThread(replies = 3)
        compose.onNodeWithText("No. 102", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Hide this post").assertExists()
    }

    @Test
    fun aSelectionStillCopiesAfterItsPostScrollsOffScreen() {
        setThread(replies = POSTS, menu = false)
        // On the first word, "Reply".
        compose
            .onNodeWithText(REPLY_2, useUnmergedTree = true)
            .performTouchInput { longClick(Offset(FIRST_WORD_X, centerY / 2)) }
        compose.waitForIdle()
        compose.onAllNodes(isPopup()).assertCountEquals(2)
        copySelection()
        val before = clipboardText()
        assertEquals("The long-pressed word is selected", "Reply", before)
        clipboard().setPrimaryClip(ClipData.newPlainText("", ""))

        // Two screens down: the selected post leaves the viewport.
        repeat(2) {
            compose.runOnIdle { listState.dispatchRawDelta(SCREEN_PX) }
            compose.waitForIdle()
        }
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
        assertTrue("The selected post must be off screen, visible: $visible", visible.min() > REPLY_2_INDEX + 2)
        val kept = compose.onAllNodesWithText(REPLY_2, useUnmergedTree = true).fetchSemanticsNodes().size
        println("ReaderThreadTextSelectionTest selected post still composed off screen: ${kept > 0}")

        copySelection()
        val after = clipboardText()
        println("ReaderThreadTextSelectionTest copied before \"$before\", after scrolling away \"$after\"")
        assertEquals("Copy after scrolling away puts the same selection on the clipboard", before, after)

        // Ending the selection lets the list dispose the post it kept for it.
        compose.onAllNodes(isRoot())[0].performTouchInput { click(center) }
        compose.waitForIdle()
        compose.onAllNodes(isPopup()).assertCountEquals(0)
        compose.onAllNodesWithText(REPLY_2, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aSelectionAcrossPostsStillCopiesAfterScrollingAway() {
        setThread(replies = POSTS, menu = false)
        val start = compose.onNodeWithText(REPLY_2, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val end = compose.onNodeWithText(reply(4), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // Long-press "Reply" in post 2, then drag into the first word of post 4.
        compose.onAllNodes(isRoot())[0].performTouchInput {
            down(Offset(start.left + FIRST_WORD_X, start.top + start.height / 4))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(Offset(end.left + FIRST_WORD_X, end.top + end.height / 4))
            up()
        }
        compose.waitForIdle()
        copySelection()
        val before = clipboardText()
        println("ReaderThreadTextSelectionTest selected across posts: \"$before\"")
        assertTrue("The selection starts in post 2: \"$before\"", before.orEmpty().startsWith("Reply text 2"))
        assertTrue("The selection runs through post 3: \"$before\"", before.orEmpty().contains(reply(3)))
        assertFalse("Headers stay out of the selection: \"$before\"", before.orEmpty().contains("No. 10"))
        clipboard().setPrimaryClip(ClipData.newPlainText("", ""))

        repeat(2) {
            compose.runOnIdle { listState.dispatchRawDelta(SCREEN_PX) }
            compose.waitForIdle()
        }
        assertTrue("Every selected post is off screen", listState.firstVisibleItemIndex > REPLY_2_INDEX + 4)
        copySelection()
        assertEquals("The whole selection copies after scrolling away", before, clipboardText())

        repeat(2) {
            compose.runOnIdle { listState.dispatchRawDelta(-SCREEN_PX) }
            compose.waitForIdle()
        }
        copySelection()
        assertEquals("And again after scrolling back", before, clipboardText())
    }

    /** Copy as the toolbar's Copy or Ctrl+C does; both call the selection's one copy. */
    private fun copySelection() {
        val toolbarCopy = toolbar.copy
        if (toolbarCopy != null) {
            compose.runOnIdle { toolbarCopy() }
        } else {
            compose.onAllNodes(isRoot())[0].performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.C) } }
        }
        compose.waitForIdle()
    }

    private fun clipboard(): ClipboardManager =
        ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)

    private fun clipboardText(): String? =
        clipboard()
            .primaryClip
            ?.getItemAt(0)
            ?.text
            ?.toString()

    private fun thread(replies: Int): Thread {
        val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(100))
        val opening =
            Post(
                id = PostId(100),
                board = key.board,
                threadId = key.thread,
                isOriginalPost = true,
                subject = "Selection",
                poster = PosterInfo(name = "Anonymous"),
                createdAtMillis = CREATED_AT,
                comment = PostComment("", persistentListOf(PostNode.Text("Opening text"))),
            )
        val rest =
            (1..replies).map { index ->
                opening.copy(
                    id = PostId(100L + index),
                    isOriginalPost = false,
                    subject = null,
                    comment =
                        PostComment(
                            "",
                            persistentListOf(
                                PostNode.Text(reply(index)),
                            ),
                        ),
                )
            }
        return Thread(key, opening, rest.toPersistentList(), ThreadStats(replyCount = replies))
    }

    private companion object {
        const val CREATED_AT = 1_794_398_520_000L
        const val POSTS = 60
        const val SCROLL_STEPS = 20
        const val SCROLL_STEP_PX = 400f
        const val CONTAINER_SOURCE = "C(SelectionContainer)"
        const val SCREEN_PX = 844f
        const val FIRST_WORD_X = 12f
        const val REPLY_2_INDEX = 3
        val REPLY_2 = reply(2)

        fun reply(index: Int) = "Reply text $index with enough words to wrap onto a second line."
    }
}

/** Records the Copy action a selection offers, as the platform toolbar would show it. */
private class RecordingTextToolbar : TextToolbar {
    var copy: (() -> Unit)? = null
    override var status: TextToolbarStatus = TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        copy = onCopyRequested
        status = TextToolbarStatus.Shown
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
    }
}

/** Robolectric gives the platform magnifier no surface to tear down when a selection ends. */
@Implements(Magnifier::class)
class SurfacelessMagnifier {
    @Implementation
    fun dismiss() = Unit
}
