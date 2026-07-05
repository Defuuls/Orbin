package com.orbin.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * A post comment parsed into a structured tree of nodes. Engines return comments as HTML; the
 * provider/data layer parses that once into this provider-agnostic tree so the UI layer never
 * touches HTML and rendering stays fast and testable.
 *
 * Keeping the parse result immutable means it can be hoisted into Compose state without causing
 * extra recompositions.
 */
data class PostComment(
    /** Original engine markup, retained for "copy text" and debugging. */
    val raw: String,
    val nodes: ImmutableList<PostNode>,
) {
    /** Quote links (`>>123`) found anywhere in the comment, used to build the reply graph. */
    val quotedPosts: List<PostId>
        get() = buildList { collectQuotes(nodes, this) }

    /** External hyperlinks (`PostNode.Link.url`) found anywhere in the comment. */
    val externalLinks: List<String>
        get() = buildList { collectLinks(nodes, this) }

    companion object {
        val Empty = PostComment(raw = "", nodes = persistentListOf())

        private fun collectQuotes(
            nodes: List<PostNode>,
            out: MutableList<PostId>,
        ) {
            nodes.forEach { node ->
                when (node) {
                    is PostNode.QuoteLink -> out.add(node.target)
                    is PostNode.Styled -> collectQuotes(node.children, out)
                    is PostNode.Link -> collectQuotes(node.children, out)
                    else -> Unit
                }
            }
        }

        private fun collectLinks(
            nodes: List<PostNode>,
            out: MutableList<String>,
        ) {
            nodes.forEach { node ->
                when (node) {
                    is PostNode.Link -> {
                        out.add(node.url)
                        collectLinks(node.children, out)
                    }
                    is PostNode.Styled -> collectLinks(node.children, out)
                    else -> Unit
                }
            }
        }
    }
}

/** An inline style that can wrap a run of child nodes. */
enum class InlineStyle {
    GREENTEXT,
    QUOTE_TEXT, // pink/orange "post quote" style used by some engines
    SPOILER,
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH,
    CODE,
    HEADING,
}

/** A node in a parsed post comment. The tree is intentionally shallow and cheap to render. */
sealed interface PostNode {
    /** Plain text run. */
    data class Text(
        val text: String,
    ) : PostNode

    /** Hard line break. */
    data object LineBreak : PostNode

    /** A run of [children] rendered with [style]. Styles may nest (e.g. bold inside greentext). */
    data class Styled(
        val style: InlineStyle,
        val children: ImmutableList<PostNode>,
    ) : PostNode

    /**
     * A cross-reference to another post (`>>123` / `>>>/b/123`). [isDead] marks references to
     * posts that no longer exist; [board] is set for cross-board quotes.
     */
    data class QuoteLink(
        val target: PostId,
        val board: BoardId? = null,
        val isOp: Boolean = false,
        val isDead: Boolean = false,
    ) : PostNode

    /** An external hyperlink. */
    data class Link(
        val url: String,
        val children: ImmutableList<PostNode>,
    ) : PostNode
}
