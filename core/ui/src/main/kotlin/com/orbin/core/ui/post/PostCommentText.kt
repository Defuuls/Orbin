package com.orbin.core.ui.post

import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
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

private val plainTextUrlRegex = Regex("""(?i)\b(?:https?://|www\.)[^\s<>\"']+""")
private val trailingUrlPunctuation = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']')

/**
 * Renders a parsed [PostComment] as styled, clickable text. Quote links invoke [onQuoteClick];
 * external links invoke [onLinkClick]. Plain-text URLs are linkified too, so thread link export and
 * in-post tapping are not limited to provider-supplied HTML anchors. Set [selectable] for full post
 * views so long-press selection exposes the platform copy menu.
 *
 * TODO(spoiler-reveal): spoilers currently render blacked-out; add tap-to-reveal per span.
 */
@Composable
fun PostCommentText(
    comment: PostComment,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    onQuoteClick: (PostId) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    onClick: () -> Unit = {},
) {
    val annotated =
        remember(comment) {
            buildAnnotatedString {
                comment.nodes.forEach { appendNode(it) }
            }
        }

    if (selectable) {
        SelectionContainer {
            ClickablePostText(
                annotated = annotated,
                modifier = modifier,
                onQuoteClick = onQuoteClick,
                onLinkClick = onLinkClick,
                onClick = onClick,
            )
        }
    } else {
        ClickablePostText(
            annotated = annotated,
            modifier = modifier,
            onQuoteClick = onQuoteClick,
            onLinkClick = onLinkClick,
            onClick = onClick,
        )
    }
}

@Composable
private fun ClickablePostText(
    annotated: AnnotatedString,
    modifier: Modifier,
    onQuoteClick: (PostId) -> Unit,
    onLinkClick: (String) -> Unit,
    onClick: () -> Unit,
) {
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            annotated.getStringAnnotations(TAG_QUOTE, offset, offset).firstOrNull()?.let {
                it.item.toLongOrNull()?.let { id -> onQuoteClick(PostId(id)) }
                return@ClickableText
            }
            annotated.getStringAnnotations(TAG_LINK, offset, offset).firstOrNull()?.let {
                onLinkClick(it.item)
                return@ClickableText
            }
            onClick()
        },
    )
}

private fun AnnotatedString.Builder.appendNode(
    node: PostNode,
    linkifyPlainText: Boolean = true,
) {
    when (node) {
        is PostNode.Text -> {
            if (linkifyPlainText) {
                appendPlainTextWithLinks(node.text)
            } else {
                append(node.text)
            }
        }
        PostNode.LineBreak -> append('\n')
        is PostNode.QuoteLink -> appendQuoteLink(node)
        is PostNode.Link -> {
            val url = normalizePlainTextUrl(node.url)
            pushStringAnnotation(TAG_LINK, url)
            withStyle(SpanStyle(color = QuoteLinkColor, textDecoration = TextDecoration.Underline)) {
                node.children.forEach { appendNode(it, linkifyPlainText = false) }
            }
            pop()
        }
        is PostNode.Styled -> appendStyled(node, linkifyPlainText)
    }
}

private fun AnnotatedString.Builder.appendPlainTextWithLinks(text: String) {
    var nextStart = 0
    plainTextUrlRegex.findAll(text).forEach { match ->
        append(text.substring(nextStart, match.range.first))

        val rawMatch = match.value
        val displayUrl = trimTrailingUrlPunctuation(rawMatch)
        val trailing = rawMatch.substring(displayUrl.length)

        val url = normalizePlainTextUrl(displayUrl)
        pushStringAnnotation(TAG_LINK, url)
        withStyle(SpanStyle(color = QuoteLinkColor, textDecoration = TextDecoration.Underline)) {
            append(displayUrl)
        }
        pop()
        append(trailing)

        nextStart = match.range.last + 1
    }
    append(text.substring(nextStart))
}

private fun AnnotatedString.Builder.appendQuoteLink(node: PostNode.QuoteLink) {
    // Local val: smart-casting a public property from another module isn't allowed.
    val board = node.board
    val prefix = if (board != null) ">>>/${board.value}/" else ">>"
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

private fun AnnotatedString.Builder.appendStyled(
    node: PostNode.Styled,
    linkifyPlainText: Boolean,
) {
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
        node.children.forEach { appendNode(it, linkifyPlainText = linkifyPlainText) }
    }
}

private fun trimTrailingUrlPunctuation(url: String): String {
    var end = url.length
    while (end > 0 && url[end - 1] in trailingUrlPunctuation) {
        end -= 1
    }
    return url.substring(0, end)
}

private fun normalizePlainTextUrl(url: String): String =
    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
        url
    } else {
        "https://$url"
    }
