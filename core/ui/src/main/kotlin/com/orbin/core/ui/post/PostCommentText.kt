package com.orbin.core.ui.post

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.orbin.core.designsystem.theme.GreentextColor
import com.orbin.core.designsystem.theme.QuoteLinkColor
import com.orbin.core.designsystem.theme.SpoilerBackground
import com.orbin.core.model.InlineStyle
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode

private const val TAG_QUOTE = "quote"
private const val TAG_LINK = "link"

/**
 * Renders a parsed [PostComment] as styled, clickable text. Quote links invoke [onQuoteClick];
 * external links invoke [onLinkClick]. The comment is converted to an [AnnotatedString] once and
 * memoized, so scrolling a long thread does not re-walk the node tree every recomposition.
 *
 * TODO(spoiler-reveal): spoilers currently render blacked-out; add tap-to-reveal per span.
 */
@Composable
fun PostCommentText(
    comment: PostComment,
    modifier: Modifier = Modifier,
    onQuoteClick: (PostId) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val annotated =
        remember(comment, onSurface) {
            buildAnnotatedString {
                comment.nodes.forEach { appendNode(it) }
            }
        }

    ClickableText(
        text = annotated,
        modifier = modifier,
        style = LocalTextStyle.current.copy(color = onSurface),
        onClick = { offset ->
            annotated.getStringAnnotations(TAG_QUOTE, offset, offset).firstOrNull()?.let {
                it.item.toLongOrNull()?.let { id -> onQuoteClick(PostId(id)) }
                return@ClickableText
            }
            annotated.getStringAnnotations(TAG_LINK, offset, offset).firstOrNull()?.let {
                onLinkClick(it.item)
            }
        },
    )
}

private fun AnnotatedString.Builder.appendNode(node: PostNode) {
    when (node) {
        is PostNode.Text -> append(node.text)
        PostNode.LineBreak -> append('\n')
        is PostNode.QuoteLink -> appendQuoteLink(node)
        is PostNode.Link -> {
            pushStringAnnotation(TAG_LINK, node.url)
            withStyle(SpanStyle(color = QuoteLinkColor, textDecoration = TextDecoration.Underline)) {
                node.children.forEach { appendNode(it) }
            }
            pop()
        }
        is PostNode.Styled -> appendStyled(node)
    }
}

private fun AnnotatedString.Builder.appendQuoteLink(node: PostNode.QuoteLink) {
    val prefix = if (node.board != null) ">>>/${node.board.value}/" else ">>"
    if (node.isDead) {
        withStyle(SpanStyle(color = QuoteLinkColor, textDecoration = TextDecoration.LineThrough)) {
            append("$prefix${node.target.value}")
        }
        return
    }
    pushStringAnnotation(TAG_QUOTE, node.target.value.toString())
    withStyle(SpanStyle(color = QuoteLinkColor, textDecoration = TextDecoration.Underline)) {
        append("$prefix${node.target.value}")
    }
    pop()
}

private fun AnnotatedString.Builder.appendStyled(node: PostNode.Styled) {
    val span =
        when (node.style) {
            InlineStyle.GREENTEXT -> SpanStyle(color = GreentextColor)
            InlineStyle.QUOTE_TEXT -> SpanStyle(color = GreentextColor)
            InlineStyle.SPOILER -> SpanStyle(color = SpoilerBackground, background = SpoilerBackground)
            InlineStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            InlineStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            InlineStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
            InlineStyle.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            InlineStyle.CODE -> SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            InlineStyle.HEADING -> SpanStyle(fontWeight = FontWeight.Bold)
        }
    withStyle(span) {
        node.children.forEach { appendNode(it) }
    }
}
