package com.orbin.core.model.stream

/** A piece of one line of post text. Never markup: renderers draw it as plain text. */
sealed interface PostPart {
    data class Text(
        val value: String,
    ) : PostPart

    /** `>>123`, a reference to another post. */
    data class Ref(
        val raw: String,
        val postNo: Long,
    ) : PostPart

    /** An `https` link, shown as text (never made tappable) with room for a clip button. */
    data class Link(
        val url: String,
    ) : PostPart
}

/** One line of a post. [isQuote] is greentext: a single leading `>`, which is not part of [parts]. */
data class PostLine(
    val isQuote: Boolean,
    val parts: List<PostPart>,
)

/** A post's number and text, the minimum needed to work out who replies to whom. */
data class PostSource(
    val no: Long,
    val text: String,
)

/** Splits post text into plain segments, so the UI never sees markup. */
object PostText {
    const val MAX_LINK_LENGTH = 500
    private const val ELLIPSIS = "…"
    private const val MAX_SAFE_LINK_LENGTH = 2000

    private val TOKEN = Regex(""">>(\d{1,12})(?!\d)|https://[^\s<>"'`]+""")
    private val TRAILING_PUNCTUATION = Regex("""[.,;:!?)\]}]+$""")
    private val SAFE_LINK = Regex("""^https://[^\s<>"']+$""")

    /** True for a well-formed, bounded `https` URL. Anything else is never treated as a link. */
    fun isSafeLink(url: String?): Boolean = url != null && url.length <= MAX_SAFE_LINK_LENGTH && SAFE_LINK.matches(url)

    fun parse(text: String?): List<PostLine> =
        text.orEmpty().split('\n').map { raw ->
            val quote = raw.startsWith('>') && !raw.startsWith(">>")
            PostLine(quote, parts(if (quote) raw.drop(1) else raw))
        }

    /**
     * For each post number, the numbers of later posts that reference it, in reading order and
     * without duplicates. References to posts that are not present, or to itself, are ignored.
     */
    fun backlinks(posts: List<PostSource>): Map<Long, List<Long>> {
        val present = posts.mapTo(HashSet()) { it.no }
        val out = LinkedHashMap<Long, MutableList<Long>>()
        for (post in posts) {
            for (target in refsOf(post.text).filter { it in present && it != post.no }) {
                val list = out.getOrPut(target) { mutableListOf() }
                if (post.no !in list) list.add(post.no)
            }
        }
        return out
    }

    /**
     * [lines] cut down to at most [maxLines] lines and [maxChars] characters of text, so one huge
     * post cannot make the stream heavy. Text is cut with an ellipsis; a reference or link is kept
     * whole or dropped, never split. Nothing is added when nothing was cut.
     */
    fun bound(
        lines: List<PostLine>,
        maxChars: Int,
        maxLines: Int,
    ): List<PostLine> {
        var budget = maxChars.coerceAtLeast(0)
        val kept = mutableListOf<PostLine>()
        var cut = lines.size > maxLines.coerceAtLeast(0)
        for (line in lines.take(maxLines.coerceAtLeast(0))) {
            val parts = mutableListOf<PostPart>()
            for (part in line.parts) {
                val size = sizeOf(part)
                if (size <= budget) {
                    parts += part
                    budget -= size
                } else {
                    if (part is PostPart.Text && budget > 0) parts += PostPart.Text(safePrefix(part.value, budget))
                    budget = 0
                    cut = true
                    break
                }
            }
            if (parts.isNotEmpty() || line.parts.isEmpty()) kept += PostLine(line.isQuote, parts)
            if (budget == 0 && cut) break
        }
        if (cut) kept += PostLine(false, listOf(PostPart.Text(ELLIPSIS)))
        return kept
    }

    private fun safePrefix(
        text: String,
        length: Int,
    ): String = text.take(length).let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }

    private fun sizeOf(part: PostPart): Int =
        when (part) {
            is PostPart.Text -> part.value.length
            is PostPart.Ref -> part.raw.length
            is PostPart.Link -> part.url.length
        }

    private fun refsOf(text: String): List<Long> =
        parse(text).flatMap { it.parts }.filterIsInstance<PostPart.Ref>().map { it.postNo }

    private fun linkOf(raw: String): String? {
        val url = raw.replace(TRAILING_PUNCTUATION, "")
        return url.takeIf { it.length <= MAX_LINK_LENGTH && isSafeLink(it) }
    }

    private fun parts(body: String): List<PostPart> {
        val parts = mutableListOf<PostPart>()
        var last = 0
        for (match in TOKEN.findAll(body)) {
            val digits = match.groups[1]?.value
            val url = if (digits == null) linkOf(match.value) else null
            if (digits == null && url == null) continue // an unusable link stays plain text
            val start = match.range.first
            if (start > last) parts += PostPart.Text(body.substring(last, start))
            if (digits != null) {
                parts += PostPart.Ref(match.value, digits.toLong())
                last = match.range.last + 1
            } else if (url != null) {
                parts += PostPart.Link(url)
                last = start + url.length
            }
        }
        if (last < body.length) parts += PostPart.Text(body.substring(last))
        return parts
    }
}
