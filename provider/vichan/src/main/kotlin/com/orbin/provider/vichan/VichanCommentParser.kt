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
        val (nodes, _) = parseNodes(tokens, 0, stopTag = null, depth = 0)
        return PostComment(raw = html, nodes = nodes)
    }

    // region tokenizer

    private sealed interface Token {
        data class Text(
            val value: String,
        ) : Token

        data class Open(
            val name: String,
            val cssClass: String?,
            val href: String?,
        ) : Token

        data class Close(
            val name: String,
        ) : Token

        data class Void(
            val name: String,
        ) : Token
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

    private fun appendText(
        tokens: MutableList<Token>,
        raw: String,
    ) {
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

    private fun attr(
        tagBody: String,
        attr: String,
    ): String? {
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
        depth: Int,
    ): Pair<ImmutableList<PostNode>, Int> {
        val out = mutableListOf<PostNode>()
        var i = start
        while (i < tokens.size) {
            when (val token = tokens[i]) {
                is Token.Text -> {
                    out.add(PostNode.Text(token.value))
                    i++
                }
                is Token.Void -> {
                    if (token.name == "br") out.add(PostNode.LineBreak)
                    i++
                }
                is Token.Close -> {
                    // Matching close ends this context; a stray close is ignored (lenient parsing).
                    if (token.name == stopTag) return out.toImmutableList() to (i + 1)
                    i++
                }
                is Token.Open -> i = parseOpenTag(tokens, i, token, depth, out)
            }
        }
        return out.toImmutableList() to i
    }

    /**
     * Handles an open tag at [openIndex]: recurses into its children and appends the built node (or
     * splices children for unknown tags), returning the next index. Beyond [MAX_DEPTH] the wrapper
     * is dropped without recursing, so pathologically nested markup can't overflow the stack.
     */
    private fun parseOpenTag(
        tokens: List<Token>,
        openIndex: Int,
        open: Token.Open,
        depth: Int,
        out: MutableList<PostNode>,
    ): Int {
        if (depth >= MAX_DEPTH) return openIndex + 1
        val (children, next) = parseNodes(tokens, openIndex + 1, open.name, depth + 1)
        val node = buildNode(open, children)
        if (node != null) out.add(node) else out.addAll(children)
        return next
    }

    /** Builds a node for a known tag, or returns null to signal "transparent / unknown tag". */
    private fun buildNode(
        open: Token.Open,
        children: ImmutableList<PostNode>,
    ): PostNode? {
        val cls = open.cssClass?.lowercase().orEmpty()
        return when {
            open.name == "a" && cls.contains("quotelink") -> quoteLink(open.href, children, dead = false)
            cls.contains("deadlink") -> quoteLink(open.href, children, dead = true)
            cls.contains("quote") || open.name == "blockquote" -> styled(InlineStyle.GREENTEXT, children)
            cls.contains("heading") -> styled(InlineStyle.HEADING, children)
            cls.contains("spoiler") || open.name == "s" -> styled(InlineStyle.SPOILER, children)
            open.name == "a" -> {
                val href = sanitizeLinkHref(open.href)
                if (href == null) null else PostNode.Link(href, children)
            }
            open.name == "strong" || open.name == "b" -> styled(InlineStyle.BOLD, children)
            open.name == "em" || open.name == "i" -> styled(InlineStyle.ITALIC, children)
            open.name == "u" -> styled(InlineStyle.UNDERLINE, children)
            open.name == "strike" || open.name == "del" -> styled(InlineStyle.STRIKETHROUGH, children)
            open.name == "pre" || open.name == "code" -> styled(InlineStyle.CODE, children)
            else -> null
        }
    }

    private fun styled(
        style: InlineStyle,
        children: ImmutableList<PostNode>,
    ): PostNode = PostNode.Styled(style, children)

    private fun quoteLink(
        href: String?,
        children: List<PostNode>,
        dead: Boolean,
    ): PostNode {
        val safeHref = sanitizeLinkHref(href)
        val text = children.joinToString("") { (it as? PostNode.Text)?.text ?: "" }
        val board = safeHref?.let { BOARD_IN_PATH.find(it)?.groupValues?.get(1) }?.let(::BoardId)
        val target =
            safeHref?.let {
                POST_IN_HREF
                    .find(it)
                    ?.groupValues
                    ?.get(1)
                    ?.toLongOrNull()
            }
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

    private fun sanitizeLinkHref(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.any { it.isISOControl() || it.isWhitespace() }) return null

        val lowered = trimmed.lowercase()
        if (isUnsafeLinkScheme(lowered)) {
            return null
        }

        return runCatching { java.net.URI(trimmed) }.getOrNull()?.let { uri ->
            val scheme = uri.scheme?.lowercase()
            when {
                scheme == null ->
                    trimmed.takeIf {
                        it.startsWith("#") || it.startsWith("/") || it.startsWith(".") || it.startsWith("?")
                    }

                scheme in SAFE_LINK_SCHEMES -> trimmed
                else -> null
            }
        } ?: trimmed.takeIf {
            it.startsWith("#") || it.startsWith("/") || it.startsWith(".") || it.startsWith("?")
        }
    }

    private fun isUnsafeLinkScheme(lowered: String): Boolean = UNSAFE_LINK_SCHEMES.any { lowered.startsWith(it) }

    private val POST_IN_HREF = Regex("""#[pq]?(\d+)""")
    private val BOARD_IN_PATH = Regex("""/([a-z0-9]+)/(?:res|thread)/""")
    private val NUMBER = Regex("""\d+""")
    private val SAFE_LINK_SCHEMES = setOf("http", "https")
    private val UNSAFE_LINK_SCHEMES =
        listOf(
            "javascript:",
            "data:",
            "vbscript:",
            "file:",
            "about:",
            "blob:",
            "jar:",
            "intent:",
        )

    private val entityMap =
        mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
            "nbsp" to " ",
            "#039" to "'",
            "#39" to "'",
        )

    /**
     * Decodes the small set of HTML entities vichan/4chan engines emit, in any free-text field
     * (comment body, subject, poster name, board title/description) - not just parsed comments.
     */
    internal fun decodeEntities(input: String): String {
        if ('&' !in input) return input
        return buildString(input.length) {
            var i = 0
            while (i < input.length) {
                val c = input[i]
                if (c == '&') {
                    val semi = input.indexOf(';', i)
                    if (semi in (i + 1)..(i + MAX_ENTITY_LENGTH)) {
                        val entity = input.substring(i + 1, semi)
                        val decoded = entityMap[entity] ?: decodeNumeric(entity)
                        if (decoded != null) {
                            append(decoded)
                            i = semi + 1
                            continue
                        }
                    }
                }
                append(c)
                i++
            }
        }
    }

    private fun decodeNumeric(entity: String): String? {
        if (!entity.startsWith("#")) return null
        val code =
            if (entity.startsWith("#x") || entity.startsWith("#X")) {
                entity.drop(HEX_PREFIX_LENGTH).toIntOrNull(RADIX_HEX)
            } else {
                entity.drop(1).toIntOrNull()
            } ?: return null
        if (!code.isAllowedEntityCodePoint()) return ""
        return String(Character.toChars(code))
    }

    private fun Int.isAllowedEntityCodePoint(): Boolean {
        val isAllowedControl = this == CHARACTER_TAB || this == CHARACTER_LINE_FEED || this == CHARACTER_CARRIAGE_RETURN
        val isVisibleScalar = this in MIN_VISIBLE_CODE_POINT..Character.MAX_CODE_POINT && this !in SURROGATE_RANGE
        return isAllowedControl || isVisibleScalar
    }

    private const val MAX_ENTITY_LENGTH = 10
    private const val HEX_PREFIX_LENGTH = 2
    private const val RADIX_HEX = 16
    private const val CHARACTER_TAB = 0x09
    private const val CHARACTER_LINE_FEED = 0x0A
    private const val CHARACTER_CARRIAGE_RETURN = 0x0D
    private const val MIN_VISIBLE_CODE_POINT = 0x20
    private val SURROGATE_RANGE = Character.MIN_SURROGATE.code..Character.MAX_SURROGATE.code

    /** Hard cap on tag nesting; pathological input beyond this is flattened, not recursed. */
    private const val MAX_DEPTH = 64
}
