package com.orbin.core.reader

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import com.orbin.core.model.BoardId
import com.orbin.core.model.InlineStyle
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.uinext.NextTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderPostCommentJourneyTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun quoteAndExternalLinksUseCurrentCallbacksAndSpoilerTapRevealsBeforeNavigation() {
        val actions = CopyOnWriteArrayList<String>()
        var callbackVersion by mutableStateOf("first")
        val row = runBlocking { thread().preparePresentation() }.rows.single()
        composeRule.setContent {
            val version = callbackVersion
            NextTheme(darkTheme = false) {
                ReaderPostComment(
                    row,
                    onQuoteClick = { actions.add("$version:quote:${it.value}") },
                    onLinkClick = { actions.add("$version:link:$it") },
                )
            }
        }

        clickText(">>2")
        composeRule.waitUntil { actions.size == 1 }
        assertEquals("first:quote:2", actions.last())
        composeRule.runOnIdle { callbackVersion = "second" }
        clickText(">>2")
        composeRule.waitUntil { actions.size == 2 }
        assertEquals("second:quote:2", actions.last())
        clickText("source")
        composeRule.waitUntil { actions.size == 3 }
        assertEquals("second:link:https://example.org/page", actions.last())

        clickText(">>3")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val layout = bodyLayout()
            val start =
                layout.layoutInput.text.text
                    .indexOf(">>3")
            layout.layoutInput.text
                .getLinkAnnotations(start, start + 3)
                .none { (it.item as LinkAnnotation.Clickable).tag == "spoiler" }
        }
        assertEquals(3, actions.size)
        clickText(">>3")
        composeRule.waitUntil { actions.size == 4 }
        assertEquals("second:quote:3", actions.last())
        clickText("clear source")
        composeRule.waitForIdle()
        assertEquals("cleartext links must not reach the host browser callback", 4, actions.size)
    }

    @Test
    fun aPreparedQuoteKeepsCanonicalLeftToRightSyntaxInsideRtlProseAndItsClickRange() {
        val key = ThreadKey(ProviderId("site"), BoardId("g"), ThreadId(1))
        val comment =
            PostComment(
                "",
                persistentListOf(
                    PostNode.Text("قراءة "),
                    PostNode.QuoteLink(PostId(123)),
                    PostNode.Text(" والتفاصيل"),
                ),
            )
        val source = Thread(key, Post(PostId(1), key.board, key.thread, isOriginalPost = true, comment = comment))
        val row = runBlocking { source.preparePresentation() }.rows.single()
        val targets = CopyOnWriteArrayList<PostId>()
        composeRule.setContent {
            NextTheme(darkTheme = false) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ReaderPostComment(row, onQuoteClick = { targets.add(it) })
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        val body = composeRule.onNodeWithText("قراءة", substring = true, useUnmergedTree = true)
        body.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val text = layout.layoutInput.text
        val start = text.text.indexOf(">>123")
        val link = text.getLinkAnnotations(start, start + 5).single()
        assertEquals("\u2066>>123\u2069", text.text.substring(link.start, link.end))
        val leftEdges = (start until start + 5).map { layout.getBoundingBox(it).left }
        assertTrue(
            "quote prefix and digits must stay in canonical LTR order: leftEdges=$leftEdges",
            leftEdges.zipWithNext().all {
                it.first <
                    it.second
            },
        )
        body.performTouchInput { click(layout.getBoundingBox(start + 3).center) }
        composeRule.waitUntil { targets.size == 1 }
        assertEquals(PostId(123), targets.single())
    }

    private fun clickText(fragment: String) {
        val layout = bodyLayout()
        val start =
            layout.layoutInput.text.text
                .indexOf(fragment)
        check(start >= 0)
        val bounds = layout.getBoundingBox(start + fragment.length / 2)
        composeRule
            .onNodeWithText("Body:", substring = true, useUnmergedTree = true)
            .performTouchInput { click(bounds.center) }
    }

    private fun bodyLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule
            .onNodeWithText("Body:", substring = true, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun thread(): Thread {
        val key = ThreadKey(ProviderId("site"), BoardId("g"), ThreadId(1))
        val comment =
            PostComment(
                "",
                persistentListOf(
                    PostNode.Text("Body: "),
                    PostNode.QuoteLink(PostId(2)),
                    PostNode.Text(" "),
                    PostNode.Link("https://example.org/page", persistentListOf(PostNode.Text("source"))),
                    PostNode.Text(" "),
                    PostNode.Styled(InlineStyle.SPOILER, persistentListOf(PostNode.QuoteLink(PostId(3)))),
                    PostNode.Text(" "),
                    PostNode.Link("http://example.org", persistentListOf(PostNode.Text("clear source"))),
                ),
            )
        return Thread(key, Post(PostId(1), key.board, key.thread, isOriginalPost = true, comment = comment))
    }
}
