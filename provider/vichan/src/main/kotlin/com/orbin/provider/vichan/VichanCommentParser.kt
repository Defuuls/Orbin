package com.orbin.provider.vichan

import com.orbin.core.model.BoardId
import com.orbin.core.model.InlineStyle
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Parses vichan/4chan post HTML into the engine-agnostic [PostComment] tree. We deliberately do
 * not pull in a full HTML library: post markup uses a tiny, well-known subset of tags, and a
 * focused tokenizer keeps the hot path allocation-light and fully unit-testable.
 *
 * Recognised markup:
 * - `<br>`                         → line break
 * - `<span class="quote">`         → greentext
 * - `<span class="heading">`       → heading
 * - `<span class="spoiler">`, `<s>`→ spoiler
 * - `<span class="deadlink">`      → dead quote link (`>>123` to a missing post)
 * - `<a class="quotelink" href>`   → quote link (intra/cross board)
 * - `<a href>`                     → external link
 * - `<strong>/<b>`, `<em>/<i>`, `<u>`, `<pre>/<code>` → inline styles
 * Unknown tags are transparent: their text content is preserved.
 */
object VichanCommentParser {

    fun parse(html: String?): PostComment {
        if (html.isNullOrEmpty()) return PostComment.Empty
        val tokens = tokenize(html)
        val (nodes, _) = parseNodes(tokens, 0, stopTag = null)
        return PostComment(raw = html, nodes = nodes)
    }

    // region tokenizer

    private sealed interface Token {
        data class Text(val value: String) : Token
        data class Open(val name: String, val cssClass: String?, val href: String?) : Token
        data class Close(val name: String) : Token
        data class Void(val name: String) : Token
    }

    private val voidTags = setOf("br", "wbr", "hr")

    private fun tokenize(html: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val n = html.length
        while (i < n) {
            val lt = html.indexOf('<', i)
            if (lt < 0) {
                appendText(tokens, html.substring(i))
                break
            }
            if (lt > i) appendText(tokens, html.substring(i, lt))
            val gt = html.indexOf('>', lt)
            if (gt < 0) {
                appendText(tokens, html.substring(lt))
                break
            }
            val rawTag = html.substring(lt + 1, gt).trim()
            tokens.add(parseTag(rawTag))
            i = gt + 1
        }
        return tokens
    }

    private fun appendText(tokens: MutableList<Token>, raw: String) {
        if (raw.isEmpty()) return
        tokens.add(Token.Text(decodeEntities(raw)))
    }

    private fun parseTag(raw: String): Token {
        val selfClosing = raw.endsWith("/")
        val body = raw.removeSuffix("/").trim()
        if (body.startsWith("/")) return Token.Close(body.removePrefix("/").trim().lowercase())
        val name = body.takeWhile { !it.isWhitespace() }.lowercase()
        if (name in voidTags || selfClosing) return Token.Void(name)
        return Token.Open(name, cssClass = attr(body, "class"), href = attr(body, "href"))
    }

    private fun attr(tagBody: String, attr: String): String? {
        val key = "$attr="
        val idx = tagBody.indexOf(key)
        if (idx < 0) return null
        val after = tagBody.substring(idx + key.length)
        val quote = after.firstOrNull()
        return if (quote == '"' || quote == '\'') {
            after.substring(1, after.indexOf(quote, 1).takeIf { it > 0 } ?: after.length)
        } else {
            after.takeWhile { !it.isWhitespace() }
        }
    }

    // endregion

    // region parser

    private fun parseNodes(
        tokens: List<Token>,
        start: Int,
        stopTag: String?,
    ): Pair<ImmutableList<PostNode>, Int> {
        val out = mutableListOf<PostNode>()
        var i = start
        while (i < tokens.size) {
            when (val token = tokens[i]) {
                is Token.Text -> { out.add(PostNode.Text(token.value)); i++ }
                is Token.Void -> { if (token.name == "br") out.add(PostNode.LineBreak); i++ }
                is Token.Close -> {
                    // Matching close ends this context; a stray close is ignored (lenient parsing).
                    if (token.name == stopTag) return out.toImmutableList() to (i + 1)
                    i++
                }
                is Token.Open -> {
                    val (children, next) = parseNodes(tokens, i + 1, token.name)
                    val node = buildNode(token, children)
                    // A null node means an unrecognised tag: splice its children in transparently.
                    if (node != null) out.add(node) else out.addAll(children)
                    i = next
                }
            }
        }
        return out.toImmutableList() to i
    }

    /** Builds a node for a known tag, or returns null to signal "transparent / unknown tag". */
    private fun buildNode(open: Token.Open, children: ImmutableList<PostNode>): PostNode? {
        val cls = open.cssClass?.lowercase().orEmpty()
        return when {
            open.name == "a" && cls.contains("quotelink") -> quoteLink(open.href, children, dead = false)
            cls.contains("deadlink") -> quoteLink(open.href, children, dead = true)
            cls.contains("quote") || open.name == "blockquote" -> styled(InlineStyle.GREENTEXT, children)
            cls.contains("heading") -> styled(InlineStyle.HEADING, children)
            cls.contains("spoiler") || open.name == "s" -> styled(InlineStyle.SPOILER, children)
            open.name == "a" -> PostNode.Link(open.href.orEmpty(), children)
            open.name == "strong" || open.name == "b" -> styled(InlineStyle.BOLD, children)
            open.name == "em" || open.name == "i" -> styled(InlineStyle.ITALIC, children)
            open.name == "u" -> styled(InlineStyle.UNDERLINE, children)
            open.name == "strike" || open.name == "del" -> styled(InlineStyle.STRIKETHROUGH, children)
            open.name == "pre" || open.name == "code" -> styled(InlineStyle.CODE, children)
            else -> null
        }
    }

    private fun styled(style: InlineStyle, children: ImmutableList<PostNode>): PostNode =
        PostNode.Styled(style, children)

    private fun quoteLink(href: String?, children: List<PostNode>, dead: Boolean): PostNode {
        val text = children.joinToString("") { (it as? PostNode.Text)?.text ?: "" }
        val board = href?.let { BOARD_IN_PATH.find(it)?.groupValues?.get(1) }?.let(::BoardId)
        val target = href?.let { POST_IN_HREF.find(it)?.groupValues?.get(1)?.toLongOrNull() }
            ?: NUMBER.find(text)?.value?.toLongOrNull()
            ?: 0L
        return PostNode.QuoteLink(
            target = PostId(target),
            board = board,
            isDead = dead,
            isOp = false,
        )
    }

    // endregion

    private val POST_IN_HREF = Regex("""#[pq]?(\d+)""")
    private val BOARD_IN_PATH = Regex("""/([a-z0-9]+)/(?:res|thread)/""")
    private val NUMBER = Regex("""\d+""")

    private val entityMap = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "#039" to "'", "#39" to "'",
    )

    private fun decodeEntities(input: String): String {
        if ('&' !in input) return input
        return buildString(input.length) {
            var i = 0
            while (i < input.length) {
                val c = input[i]
                if (c == '&') {
                    val semi = input.indexOf(';', i)
                    if (semi in (i + 1)..(i + 10)) {
                        val entity = input.substring(i + 1, semi)
                        val decoded = entityMap[entity] ?: decodeNumeric(entity)
                        if (decoded != null) { append(decoded); i = semi + 1; continue }
                    }
                }
                append(c)
                i++
            }
        }
    }

    private fun decodeNumeric(entity: String): String? {
        if (!entity.startsWith("#")) return null
        val code = if (entity.startsWith("#x") || entity.startsWith("#X")) {
            entity.drop(2).toIntOrNull(16)
        } else {
            entity.drop(1).toIntOrNull()
        } ?: return null
        return code.toChar().toString()
    }
}
