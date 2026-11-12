package com.orbin.core.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
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
import com.orbin.uinext.ThreadScreen
import com.orbin.uinext.tokens.ReaderAmoledColors
import com.orbin.uinext.tokens.ReaderDarkColors
import com.orbin.uinext.tokens.ReaderLightColors
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderPostCommentStyleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun preparedRichBodiesUseCloverPhoneAndWidePostTypographyAndReaderInk() {
        val key = ThreadKey(ProviderId("site"), BoardId("g"), ThreadId(1))
        val thread =
            Thread(
                key,
                Post(
                    PostId(1),
                    key.board,
                    key.thread,
                    isOriginalPost = true,
                    comment =
                        PostComment("", persistentListOf(PostNode.Text("Plain body "), PostNode.QuoteLink(PostId(2)))),
                ),
            )
        val presentation = runBlocking { thread.preparePresentation() }
        var wide by mutableStateOf(false)
        composeRule.setContent {
            NextTheme(darkTheme = false) {
                ThreadScreen(
                    subject = "Thread",
                    board = "/g/",
                    posts = presentation.rowModels,
                    wideWindow = wide,
                    body = { ReaderPostComment(presentation.rows.single()) },
                )
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        composeRule
            .onNodeWithText("Plain body", substring = true, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val style = layouts.single().layoutInput.style
        assertEquals(14.sp, style.fontSize)
        assertEquals(TextUnit.Unspecified, style.lineHeight)
        assertEquals(ReaderLightColors.ink, style.color)
        val text = layouts.single().layoutInput.text
        val linkStyle =
            text
                .getLinkAnnotations(0, text.length)
                .single()
                .item.styles
                ?.style
        assertEquals(ReaderLightColors.quoteLink, linkStyle?.color)
        assertEquals(TextDecoration.Underline, linkStyle?.textDecoration)

        composeRule.runOnIdle { wide = true }
        composeRule.waitForIdle()
        val wideLayouts = mutableListOf<TextLayoutResult>()
        composeRule
            .onNodeWithText("Plain body", substring = true, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(wideLayouts) }
        assertEquals(
            16.sp,
            wideLayouts
                .single()
                .layoutInput.style.fontSize,
        )
        assertEquals(
            TextUnit.Unspecified,
            wideLayouts
                .single()
                .layoutInput.style.lineHeight,
        )
    }

    @Test
    fun preparedNormalQuoteAndHiddenSpoilerColorsFollowEveryReaderPalette() {
        val key = ThreadKey(ProviderId("site"), BoardId("g"), ThreadId(1))
        val comment =
            PostComment(
                "",
                persistentListOf(
                    PostNode.Text("Body: "),
                    PostNode.Styled(InlineStyle.GREENTEXT, persistentListOf(PostNode.Text("green"))),
                    PostNode.Text(" "),
                    PostNode.QuoteLink(PostId(2)),
                    PostNode.Text(" "),
                    PostNode.Styled(InlineStyle.SPOILER, persistentListOf(PostNode.Text("secret"))),
                ),
            )
        val thread = Thread(key, Post(PostId(1), key.board, key.thread, isOriginalPost = true, comment = comment))
        val row = runBlocking { thread.preparePresentation() }.rows.single()
        var palette by mutableStateOf(0)
        composeRule.setContent {
            NextTheme(darkTheme = palette > 0, amoled = palette == 2) { ReaderPostComment(row) }
        }

        listOf(ReaderLightColors, ReaderDarkColors, ReaderAmoledColors).forEachIndexed { index, colors ->
            composeRule.runOnIdle { palette = index }
            composeRule.waitForIdle()
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule
                .onNodeWithText("Body:", substring = true, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val text = layout.layoutInput.text
            val green = text.text.indexOf("green")
            val secret = text.text.indexOf("secret")
            assertEquals(colors.ink, layout.layoutInput.style.color)
            assertTrue(text.spanStyles.any { it.start == green && it.item.color == colors.quote })
            assertEquals(
                colors.quoteLink,
                text
                    .getLinkAnnotations(0, text.length)
                    .first()
                    .item.styles
                    ?.style
                    ?.color,
            )
            assertTrue(
                text.spanStyles.any {
                    it.start == secret &&
                        it.item.background == colors.surfaceVariant &&
                        it.item.color == colors.surfaceVariant
                },
            )
        }
    }
}
