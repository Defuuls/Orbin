package com.orbin.core.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
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
import com.orbin.uinext.NextPlatform
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ThreadScreen
import com.orbin.uinext.tokens.LocalNextFontFamily
import com.orbin.uinext.tokens.ReaderAmoledColors
import com.orbin.uinext.tokens.ReaderDarkColors
import com.orbin.uinext.tokens.ReaderLightColors
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun preparedRichBodiesUsePhoneAndWideTypographyAndPlatformReaderFont() {
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
        var platform by mutableStateOf(NextPlatform.ANDROID)
        var fontScale by mutableStateOf(1f)
        lateinit var installedFamily: FontFamily
        composeRule.setContent {
            NextTheme(darkTheme = false, platform = platform, fontScale = fontScale) {
                NextTheme {
                    installedFamily = LocalNextFontFamily.current
                    ThreadScreen(
                        subject = "Thread",
                        board = "/g/",
                        posts = presentation.rowModels,
                        wideWindow = wide,
                        body = { ReaderPostComment(presentation.rows.single()) },
                    )
                }
            }
        }

        NextPlatform.entries.forEach { intendedPlatform ->
            composeRule.runOnIdle {
                platform = intendedPlatform
                wide = false
                fontScale = 1f
            }
            composeRule.waitForIdle()
            val phone = layoutForText("Plain body", substring = true)
            val baselineScale = phone.layoutInput.density.fontScale
            assertPhoneTypographyAndLinks(phone, layoutForText("No. 1"), installedFamily, intendedPlatform)

            composeRule.runOnIdle { wide = true }
            composeRule.waitForIdle()
            val wideLayout = layoutForText("Plain body", substring = true)
            assertEquals(16.sp, wideLayout.layoutInput.style.fontSize)
            assertEquals(TextUnit.Unspecified, wideLayout.layoutInput.style.lineHeight)
            assertEquals(installedFamily, wideLayout.layoutInput.style.fontFamily)
            assertEquals(12.sp, layoutForText("No. 1").layoutInput.style.fontSize)

            composeRule.runOnIdle { fontScale = 1.5f }
            composeRule.waitForIdle()
            val scaled = layoutForText("Plain body", substring = true)
            assertEquals(baselineScale * 1.5f, scaled.layoutInput.density.fontScale, 0.001f)
            assertEquals(16.sp, scaled.layoutInput.style.fontSize)
            assertEquals(TextUnit.Unspecified, scaled.layoutInput.style.lineHeight)
            assertEquals(installedFamily, scaled.layoutInput.style.fontFamily)
        }
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

    private fun assertPhoneTypographyAndLinks(
        phone: TextLayoutResult,
        metadata: TextLayoutResult,
        installedFamily: FontFamily,
        platform: NextPlatform,
    ) {
        val style = phone.layoutInput.style
        assertEquals(15.sp, style.fontSize)
        assertEquals(TextUnit.Unspecified, style.lineHeight)
        assertEquals(ReaderLightColors.ink, style.color)
        assertEquals(installedFamily, style.fontFamily)
        if (platform == NextPlatform.IOS) {
            assertEquals(FontFamily.Default, installedFamily)
        } else {
            assertNotEquals(FontFamily.Default, installedFamily)
        }
        val metadataStyle = metadata.layoutInput.style
        assertEquals(11.sp, metadataStyle.fontSize)
        assertEquals(installedFamily, metadataStyle.fontFamily)
        assertEquals(ReaderLightColors.inkMuted, metadataStyle.color)

        val text = phone.layoutInput.text
        val linkStyle =
            text
                .getLinkAnnotations(0, text.length)
                .single()
                .item.styles
                ?.style
        assertEquals(ReaderLightColors.quoteLink, linkStyle?.color)
        assertEquals(TextDecoration.Underline, linkStyle?.textDecoration)
    }

    private fun layoutForText(
        text: String,
        substring: Boolean = false,
    ): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule
            .onNodeWithText(text, substring = substring, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }
}
