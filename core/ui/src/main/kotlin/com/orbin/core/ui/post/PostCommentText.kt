package com.orbin.core.ui.post

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.orbin.core.designsystem.theme.GreentextColor
import com.orbin.core.designsystem.theme.QuoteLinkColor
import com.orbin.core.designsystem.theme.SpoilerBackground
import com.orbin.core.model.InlineStyle
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode

private val quoteLinkStyle = SpanStyle(color = QuoteLinkColor, textDecoration = TextDecoration.Underline)
private val quoteLinkStyles = TextLinkStyles(style = quoteLinkStyle)

private val plainTextUrlRegex = Regex("""(?i)\b(?:https?://|www\.)[^\s<>\"']+""")
private val trailingUrlPunctuation = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']')

/**
 * Renders a parsed [PostComment] as styled, interactive text. Quote links invoke [onQuoteClick];
 * external links invoke [onLinkClick]. Plain-text URLs are linkified too, so thread link export and
 * in-post tapping are not limited to provider-supplied HTML anchors. Set [selectable] for full post
 * views so long-press selection exposes the platform copy menu.
 *
 * Uses [LinkAnnotation] so links are exposed to accessibility services (TalkBack announces them as
 * links) and honour the platform's link handling, replacing the deprecated `ClickableText`.
 *
 * For non-interactive previews (catalog cards, feed rows) use [PostCommentPreviewText] instead, so
 * taps fall through to the enclosing card.
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
) {
    // Keep the latest callbacks without rebuilding the (comment-keyed) annotated string on every
    // recomposition; the link listeners read these at click time.
    val currentOnQuoteClick by rememberUpdatedState(onQuoteClick)
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)

    val annotated =
        remember(comment) {
            buildAnnotatedString {
                comment.nodes.forEach {
                    appendNode(
                        node = it,
                        onQuoteClick = { id -> currentOnQuoteClick(id) },
                        onLinkClick = { url -> currentOnLinkClick(url) },
                    )
                }
            }
        }

    val text =
        @Composable {
            Text(
                text = annotated,
                modifier = modifier,
                style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
            )
        }

    if (selectable) {
        SelectionContainer(content = text)
    } else {
        text()
    }
}

/**
 * Renders a [PostComment] as styled but non-interactive text for previews. Quote links and URLs are
 * coloured for consistency but are not tappable, so taps fall through to the enclosing clickable
 * card (e.g. opening the thread) instead of being swallowed.
 */
@Composable
fun PostCommentPreviewText(
    comment: PostComment,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val annotated =
        remember(comment) {
            buildAnnotatedString {
                comment.nodes.forEach { appendNode(node = it, interactive = false) }
            }
        }
    Text(
        text = annotated,
        modifier = modifier,
        style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun AnnotatedString.Builder.appendNode(
    node: PostNode,
    interactive: Boolean = true,
    linkifyPlainText: Boolean = true,
    onQuoteClick: (PostId) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
) {
    when (node) {
        is PostNode.Text ->
            if (linkifyPlainText) {
                appendPlainTextWithLinks(node.text, interactive, onLinkClick)
            } else {
                append(node.text)
            }
        PostNode.LineBreak -> append('\n')
        is PostNode.QuoteLink -> appendQuoteLink(node, interactive, onQuoteClick)
        is PostNode.Link -> appendLink(node, interactive, onQuoteClick, onLinkClick)
        is PostNode.Styled -> appendStyled(node, interactive, linkifyPlainText, onQuoteClick, onLinkClick)
    }
}

private fun AnnotatedString.Builder.appendLink(
    node: PostNode.Link,
    interactive: Boolean,
    onQuoteClick: (PostId) -> Unit,
    onLinkClick: (String) -> Unit,
) {
    val url = normalizePlainTextUrl(node.url)
    // Link children are never re-linkified: they already sit inside an anchor.
    val children: AnnotatedString.Builder.() -> Unit = {
        node.children.forEach {
            appendNode(it, interactive, linkifyPlainText = false, onQuoteClick, onLinkClick)
        }
    }
    if (interactive) {
        withLink(clickableLink { onLinkClick(url) }, children)
    } else {
        withStyle(quoteLinkStyle, children)
    }
}

private fun AnnotatedString.Builder.appendPlainTextWithLinks(
    text: String,
    interactive: Boolean,
    onLinkClick: (String) -> Unit,
) {
    var nextStart = 0
    plainTextUrlRegex.findAll(text).forEach { match ->
        append(text.substring(nextStart, match.range.first))

        val rawMatch = match.value
        val displayUrl = trimTrailingUrlPunctuation(rawMatch)
        val trailing = rawMatch.substring(displayUrl.length)
        val url = normalizePlainTextUrl(displayUrl)

        if (interactive) {
            withLink(clickableLink { onLinkClick(url) }) { append(displayUrl) }
        } else {
            withStyle(quoteLinkStyle) { append(displayUrl) }
        }
        append(trailing)

        nextStart = match.range.last + 1
    }
    append(text.substring(nextStart))
}

private fun AnnotatedString.Builder.appendQuoteLink(
    node: PostNode.QuoteLink,
    interactive: Boolean,
    onQuoteClick: (PostId) -> Unit,
) {
    // Local val: smart-casting a public property from another module isn't allowed.
    val board = node.board
    val prefix = if (board != null) ">>>/${board.value}/" else ">>"
    val label = "$prefix${node.target.value}"
    when {
        node.isDead ->
            withStyle(SpanStyle(color = QuoteLinkColor, textDecoration = TextDecoration.LineThrough)) {
                append(label)
            }
        interactive -> withLink(clickableLink { onQuoteClick(node.target) }) { append(label) }
        else -> withStyle(quoteLinkStyle) { append(label) }
    }
}

private fun AnnotatedString.Builder.appendStyled(
    node: PostNode.Styled,
    interactive: Boolean,
    linkifyPlainText: Boolean,
    onQuoteClick: (PostId) -> Unit,
    onLinkClick: (String) -> Unit,
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
            InlineStyle.CODE -> SpanStyle(fontFamily = FontFamily.Monospace)
            InlineStyle.HEADING -> SpanStyle(fontWeight = FontWeight.Bold)
        }
    withStyle(span) {
        node.children.forEach {
            appendNode(it, interactive, linkifyPlainText, onQuoteClick, onLinkClick)
        }
    }
}

/** Builds a styled, clickable link annotation that runs [onClick] when tapped. */
private fun clickableLink(onClick: () -> Unit): LinkAnnotation.Clickable =
    LinkAnnotation.Clickable(tag = "link", styles = quoteLinkStyles) { onClick() }

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
