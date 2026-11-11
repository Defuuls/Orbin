package com.orbin.core.ui.post

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

/** Theme values supplied by the presentation adapter without coupling this module to a screen. */
data class CommentColors(
    val quote: Color,
    val link: Color,
    val spoiler: Color,
)

/** A node walk and URL linkification completed before the thread reaches the UI. */
class PreparedPostComment internal constructor(
    internal val comment: PostComment,
    internal val colors: List<CommentColors>,
    private val isolateQuoteLinks: Boolean,
) {
    internal val actions = Channel<CommentAction>(Channel.BUFFERED)
    internal val texts: List<AnnotatedString> = colors.indices.map { render(it, emptySet()) }

    internal fun render(
        palette: Int,
        revealed: Set<Int>,
    ): AnnotatedString {
        val colors = colors[palette]
        return buildCommentText(
            comment,
            CommentRenderContext(
                onQuoteClick = { actions.trySend(CommentAction.Quote(it)) },
                onLinkClick = { actions.trySend(CommentAction.Link(it)) },
                onSpoilerClick = { actions.trySend(CommentAction.Spoiler(it)) },
                revealedSpoilers = revealed,
                greentextColor = colors.quote,
                quoteLinkColor = colors.link,
                revealedSpoilerBackground = colors.spoiler,
                hiddenSpoilerBackground = colors.spoiler,
                isolateQuoteLinks = isolateQuoteLinks,
            ),
        )
    }
}

internal sealed interface CommentAction {
    data class Quote(
        val id: PostId,
    ) : CommentAction

    data class Link(
        val url: String,
    ) : CommentAction

    data class Spoiler(
        val id: Int,
    ) : CommentAction
}

/** Call on a worker dispatcher as part of preparing a thread load. */
fun preparePostComment(
    comment: PostComment,
    colors: List<CommentColors>,
    isolateQuoteLinks: Boolean = false,
): PreparedPostComment {
    require(colors.isNotEmpty())
    val renderable =
        if (comment.nodes.isEmpty() && comment.raw.isNotBlank()) {
            comment.copy(nodes = persistentListOf(PostNode.Text(comment.raw.trim())))
        } else {
            comment
        }
    return PreparedPostComment(renderable, colors, isolateQuoteLinks)
}

/** Binds preformatted text to the current callbacks; spoiler rebuilds also run off the UI thread. */
@Composable
fun PreparedPostCommentText(
    prepared: PreparedPostComment,
    palette: Int,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    style: TextStyle = LocalTextStyle.current,
    onQuoteClick: (PostId) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
) {
    val onQuote by rememberUpdatedState(onQuoteClick)
    val onLink by rememberUpdatedState(onLinkClick)
    val haptics = LocalHapticFeedback.current
    var revealed by remember(prepared) { mutableStateOf(emptySet<Int>()) }
    LaunchedEffect(prepared) {
        for (action in prepared.actions) {
            when (action) {
                is CommentAction.Quote -> onQuote(action.id)
                is CommentAction.Link -> onLink(action.url)
                is CommentAction.Spoiler -> {
                    haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    revealed = revealed + action.id
                }
            }
        }
    }
    val text by produceState(prepared.texts[palette], prepared, palette, revealed) {
        value =
            if (revealed.isEmpty()) {
                prepared.texts[palette]
            } else {
                withContext(Dispatchers.Default) { prepared.render(palette, revealed) }
            }
    }
    val content = @Composable { Text(text, modifier, style = style) }
    if (selectable) SelectionContainer(content = content) else content()
}
