package com.orbin.provider.lynxchan

import com.orbin.core.model.BoardId
import com.orbin.core.model.InlineStyle
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Parses LynxChan's rendered `markdown` field (despite the name, this is HTML - see
 * [com.orbin.provider.lynxchan.api.LynxChanCatalogThread]) into the engine-agnostic [PostComment]
 * tree. Unlike vichan, LynxChan does not wrap line breaks in `<br>`; it emits literal newlines, and
 * quote-link hrefs always fully qualify the board (even for same-board quotes), so same-board vs.
 * cross-board is decided here by comparing against [currentBoard].
 *
 * Recognised markup (see the site's own `/.static/pages/posting.html` for the source syntax):
 * - literal `\n`                          -> line break
 * - `<span class="greenText">`            -> greentext
 * - `<span class="redText">`              -> heading (`==text==`)
 * - `<span class="spoiler">`              -> spoiler (`[spoiler]`/`**text**`) - best-effort class
 *   name; unverified against a live example at implementation time.
 * - `<strong>`                            -> bold (`'''text'''`)
 * - `<em>`                                -> italic (`''text''`)
 * - `<u>`                                 -> underline (`__text__`)
 * - `<s>`                                 -> strikethrough (`~~text~~`) - best-effort tag.
 * - `<a class="quoteLink" href>`          -> post quote (`>>N` / `>>>/board/N`)
 * - `<a href="/board/">` (no quoteLink)   -> board link (`>>>/board/`)
 * - `<a href>`                            -> external link
 */
internal object LynxChanCommentParser {
    fun parse(
        html: String?,
        currentBoard: String,
        siteUrl: String,
    ): PostComment {
        if (html.isNullOrEmpty()) return PostComment.Empty
        val tokens = tokenize(html)
        val context = Context(currentBoard, siteUrl)
        val (nodes, _) = parseNodes(tokens, 0, stopTag = null, depth = 0, context = context)
        return PostComment(raw = html, nodes = nodes)
    }

    private class Context(
        val currentBoard: String,
        val siteUrl: String,
    )

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

        data object LineBreak : Token
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

    /** Splits on literal newlines (LynxChan's actual line-break convention) into Text/LineBreak tokens. */
    private fun appendText(
        tokens: MutableList<Token>,
        raw: String,
    ) {
        if (raw.isEmpty()) return
        val normalized = raw.replace("\r\n", "\n")
        val lines = normalized.split('\n')
        lines.forEachIndexed { index, line ->
            if (line.isNotEmpty()) tokens.add(Token.Text(decodeEntities(line)))
            if (index != lines.lastIndex) tokens.add(Token.LineBreak)
        }
    }

    private fun parseTag(raw: String): Token {
        val body = raw.removeSuffix("/").trim()
        if (body.startsWith("/")) return Token.Close(body.removePrefix("/").trim().lowercase())
        val name = body.takeWhile { !it.isWhitespace() }.lowercase()
        if (name in voidTags) return Token.LineBreak
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
        context: Context,
    ): Pair<ImmutableList<PostNode>, Int> {
        val out = mutableListOf<PostNode>()
        var i = start
        while (i < tokens.size) {
            when (val token = tokens[i]) {
                is Token.Text -> {
                    out.add(PostNode.Text(token.value))
                    i++
                }
                Token.LineBreak -> {
                    out.add(PostNode.LineBreak)
                    i++
                }
                is Token.Close -> {
                    if (token.name == stopTag) return out.toImmutableList() to (i + 1)
                    i++
                }
                is Token.Open -> i = parseOpenTag(tokens, i, token, depth, out, context)
            }
        }
        return out.toImmutableList() to i
    }

    private fun parseOpenTag(
        tokens: List<Token>,
        openIndex: Int,
        open: Token.Open,
        depth: Int,
        out: MutableList<PostNode>,
        context: Context,
    ): Int {
        if (depth >= MAX_DEPTH) return openIndex + 1
        val (children, next) = parseNodes(tokens, openIndex + 1, open.name, depth + 1, context)
        val node = buildNode(open, children, context)
        if (node != null) out.add(node) else out.addAll(children)
        return next
    }

    private fun buildNode(
        open: Token.Open,
        children: ImmutableList<PostNode>,
        context: Context,
    ): PostNode? {
        val cls = open.cssClass?.lowercase().orEmpty()
        return when {
            open.name == "a" && cls.contains("quotelink") -> quoteLink(open.href, children, context)
            open.name == "a" -> externalOrBoardLink(open.href, children, context)
            cls.contains("greentext") -> styled(InlineStyle.GREENTEXT, children)
            cls.contains("redtext") -> styled(InlineStyle.HEADING, children)
            cls.contains("spoiler") -> styled(InlineStyle.SPOILER, children)
            open.name == "strong" || open.name == "b" -> styled(InlineStyle.BOLD, children)
            open.name == "em" || open.name == "i" -> styled(InlineStyle.ITALIC, children)
            open.name == "u" -> styled(InlineStyle.UNDERLINE, children)
            open.name == "s" || open.name == "strike" || open.name == "del" ->
                styled(
                    InlineStyle.STRIKETHROUGH,
                    children,
                )
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
        context: Context,
    ): PostNode {
        val safeHref = sanitizeLinkHref(href)
        val text = children.joinToString("") { (it as? PostNode.Text)?.text ?: "" }
        val hrefBoard = safeHref?.let { BOARD_IN_PATH.find(it)?.groupValues?.get(1) }
        val board = hrefBoard?.takeIf { it != context.currentBoard }?.let(::BoardId)
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
        return PostNode.QuoteLink(target = PostId(target), board = board, isDead = false, isOp = false)
    }

    /** A bare `<a href>` with no `quoteLink` class: either `>>>/board/` (board-only) or a genuine
     * external link. LynxChan renders board links as a site-relative path with no post fragment. */
    private fun externalOrBoardLink(
        href: String?,
        children: ImmutableList<PostNode>,
        context: Context,
    ): PostNode? {
        val safeHref = sanitizeLinkHref(href) ?: return null
        val boardOnly = BOARD_LINK.matchEntire(safeHref)?.groupValues?.get(1)
        val url = if (boardOnly != null) "${context.siteUrl}$safeHref" else safeHref
        return PostNode.Link(url, children)
    }

    // endregion

    private fun sanitizeLinkHref(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        val invalid =
            trimmed.isEmpty() ||
                trimmed.any { it.isISOControl() || it.isWhitespace() } ||
                UNSAFE_LINK_SCHEMES.any { trimmed.lowercase().startsWith(it) }
        if (invalid) return null
        if (trimmed.startsWith("/")) return trimmed

        val scheme = runCatching { java.net.URI(trimmed) }.getOrNull()?.scheme?.lowercase()
        return trimmed.takeIf { scheme in SAFE_LINK_SCHEMES }
    }

    private fun decodeEntities(input: String): String {
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

    private val BOARD_IN_PATH = Regex("""^/([a-z0-9]+)/""")
    private val BOARD_LINK = Regex("""^/([a-z0-9]+)/$""")
    private val POST_IN_HREF = Regex("""#(\d+)""")
    private val NUMBER = Regex("""\d+""")
    private val SAFE_LINK_SCHEMES = setOf("http", "https")
    private val UNSAFE_LINK_SCHEMES =
        listOf("javascript:", "data:", "vbscript:", "file:", "about:", "blob:", "jar:", "intent:")

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
