package com.orbin.core.ui.post

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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

/** Text painted in its own background colour: present for layout and selection, but unreadable. */
private val blackedOutSpoilerStyle = SpanStyle(color = SpoilerBackground, background = SpoilerBackground)

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
 * Spoilers start blacked out and reveal individually on tap; see [CommentRenderContext] for how a
 * span keeps its identity across the rebuild that reveals it. Reveals are per-composition, so
 * scrolling a post out of view and back re-hides it.
 *
 * For non-interactive previews (catalog cards, feed rows) use [PostCommentPreviewText] instead, so
 * taps fall through to the enclosing card.
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

    // Keyed on the comment so a recycled row showing a different post starts fully hidden rather
    // than inheriting the previous post's reveals.
    var revealedSpoilers by remember(comment) { mutableStateOf(emptySet<Int>()) }
    val haptics = LocalHapticFeedback.current
    val revealedSpoilerBackground = MaterialTheme.colorScheme.surfaceVariant

    val annotated =
        remember(comment, revealedSpoilers, revealedSpoilerBackground) {
            val context =
                CommentRenderContext(
                    onQuoteClick = { id -> currentOnQuoteClick(id) },
                    onLinkClick = { url -> currentOnLinkClick(url) },
                    revealedSpoilers = revealedSpoilers,
                    onSpoilerClick = { id ->
                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        revealedSpoilers = revealedSpoilers + id
                    },
                    revealedSpoilerBackground = revealedSpoilerBackground,
                )
            buildCommentText(comment, context)
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
            buildCommentText(comment, CommentRenderContext(), interactive = false)
        }
    Text(
        text = annotated,
        modifier = modifier,
        style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Everything the node walk needs that does not vary between sibling nodes: the click callbacks and
 * the spoiler reveal state.
 *
 * Spoiler spans are identified by their position in document order, handed out by [nextSpoilerId]
 * as the tree is walked. The walk is deterministic for a given [PostComment], so an id denotes the
 * same span every time — which is what lets a span that was revealed stay revealed across the
 * rebuild that its own reveal triggers. A fresh context (and therefore a fresh counter) is created
 * per build for exactly that reason.
 */
internal class CommentRenderContext(
    val onQuoteClick: (PostId) -> Unit = {},
    val onLinkClick: (String) -> Unit = {},
    val revealedSpoilers: Set<Int> = emptySet(),
    val onSpoilerClick: (Int) -> Unit = {},
    val revealedSpoilerBackground: Color = Color.Unspecified,
) {
    private var assignedSpoilers = 0

    fun nextSpoilerId(): Int = assignedSpoilers++
}

/**
 * Builds the rendered string for [comment] under [ctx].
 *
 * Separate from the composables so the rendering rules — which ranges are blacked out, which are
 * tappable, what a reveal changes — can be asserted as plain values on the JVM. The Compose test
 * API offers no way to click an individual link inside a text run, so driving this through a
 * composable could not observe a reveal at all.
 */
internal fun buildCommentText(
    comment: PostComment,
    ctx: CommentRenderContext,
    interactive: Boolean = true,
): AnnotatedString =
    buildAnnotatedString {
        comment.nodes.forEach { appendNode(node = it, ctx = ctx, interactive = interactive) }
    }

private fun AnnotatedString.Builder.appendNode(
    node: PostNode,
    ctx: CommentRenderContext,
    interactive: Boolean = true,
    linkifyPlainText: Boolean = true,
) {
    when (node) {
        is PostNode.Text ->
            if (linkifyPlainText) {
                appendPlainTextWithLinks(node.text, interactive, ctx.onLinkClick)
            } else {
                append(node.text)
            }
        PostNode.LineBreak -> append('\n')
        is PostNode.QuoteLink -> appendQuoteLink(node, interactive, ctx.onQuoteClick)
        is PostNode.Link -> appendLink(node, ctx, interactive)
        is PostNode.Styled -> appendStyled(node, ctx, interactive, linkifyPlainText)
    }
}

private fun AnnotatedString.Builder.appendLink(
    node: PostNode.Link,
    ctx: CommentRenderContext,
    interactive: Boolean,
) {
    val url = normalizePlainTextUrl(node.url)
    // Link children are never re-linkified: they already sit inside an anchor.
    val children: AnnotatedString.Builder.() -> Unit = {
        node.children.forEach {
            appendNode(it, ctx, interactive, linkifyPlainText = false)
        }
    }
    if (interactive) {
        withLink(clickableLink { ctx.onLinkClick(url) }, children)
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
    ctx: CommentRenderContext,
    interactive: Boolean,
    linkifyPlainText: Boolean,
) {
    // Spoilers are the one style whose rendering depends on state rather than on the node alone.
    if (node.style == InlineStyle.SPOILER) {
        appendSpoiler(node, ctx, interactive, linkifyPlainText)
        return
    }
    val span =
        when (node.style) {
            InlineStyle.GREENTEXT -> SpanStyle(color = GreentextColor)
            InlineStyle.QUOTE_TEXT -> SpanStyle(color = GreentextColor)
            InlineStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            InlineStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            InlineStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
            InlineStyle.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            InlineStyle.CODE -> SpanStyle(fontFamily = FontFamily.Monospace)
            InlineStyle.HEADING -> SpanStyle(fontWeight = FontWeight.Bold)
            // Handled above; listed so the when stays exhaustive without an else branch.
            InlineStyle.SPOILER -> SpanStyle()
        }
    withStyle(span) {
        node.children.forEach {
            appendNode(it, ctx, interactive, linkifyPlainText)
        }
    }
}

/**
 * Renders one spoiler span, blacked out until tapped.
 *
 * While hidden, the children are rendered **non-interactively** even in an otherwise interactive
 * comment. A quote link inside a spoiler would otherwise sit on top of the reveal target and take
 * the tap, so the first tap would navigate away to a post the reader cannot yet see they were
 * offered. Once revealed, the children regain their normal behaviour.
 */
private fun AnnotatedString.Builder.appendSpoiler(
    node: PostNode.Styled,
    ctx: CommentRenderContext,
    interactive: Boolean,
    linkifyPlainText: Boolean,
) {
    val id = ctx.nextSpoilerId()
    val children: (Boolean) -> AnnotatedString.Builder.() -> Unit = { childrenInteractive ->
        {
            node.children.forEach {
                appendNode(it, ctx, childrenInteractive, linkifyPlainText)
            }
        }
    }

    when {
        id in ctx.revealedSpoilers ->
            // Revealed: readable, but on a tinted ground so it stays visibly a spoiler.
            withStyle(SpanStyle(background = ctx.revealedSpoilerBackground), children(interactive))

        interactive ->
            withLink(spoilerLink { ctx.onSpoilerClick(id) }) {
                withStyle(blackedOutSpoilerStyle, children(false))
            }

        // Previews are not tappable, so a spoiler there simply stays hidden.
        else -> withStyle(blackedOutSpoilerStyle, children(false))
    }
}

/** Builds a styled, clickable link annotation that runs [onClick] when tapped. */
private fun clickableLink(onClick: () -> Unit): LinkAnnotation.Clickable =
    LinkAnnotation.Clickable(tag = "link", styles = quoteLinkStyles) { onClick() }

/**
 * A clickable annotation carrying no styles of its own — the blackout span supplies the appearance,
 * and link colouring would defeat the point of hiding the text.
 */
private fun spoilerLink(onClick: () -> Unit): LinkAnnotation.Clickable =
    LinkAnnotation.Clickable(tag = "spoiler", styles = null) { onClick() }

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
