package com.orbin.core.model.stream

/** A piece of one line of post text. Never markup: renderers draw it as plain text. */
sealed interface PostPart {
    data class Text(val value: String) : PostPart

    /** `>>123`, a reference to another post. */
    data class Ref(val raw: String, val postNo: Long) : PostPart

    /** An `https` link, shown as text (never made tappable) with room for a clip button. */
    data class Link(val url: String) : PostPart
}

/** One line of a post. [isQuote] is greentext: a single leading `>`, which is not part of [parts]. */
data class PostLine(val isQuote: Boolean, val parts: List<PostPart>)

/** A post's number and text, the minimum needed to work out who replies to whom. */
data class PostSource(val no: Long, val text: String)

/** Splits post text into plain segments, so the UI never sees markup. */
object PostText {
    const val MAX_LINK_LENGTH = 500
    private const val MAX_SAFE_LINK_LENGTH = 2000

    private val TOKEN = Regex(""">>(\d{1,12})(?!\d)|https://[^\s<>"'`]+""")
    private val TRAILING_PUNCTUATION = Regex("""[.,;:!?)\]}]+$""")
    private val SAFE_LINK = Regex("""^https://[^\s<>"']+$""")

    /** True for a well-formed, bounded `https` URL. Anything else is never treated as a link. */
    fun isSafeLink(url: String?): Boolean =
        url != null && url.length <= MAX_SAFE_LINK_LENGTH && SAFE_LINK.matches(url)

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
            for (line in parse(post.text)) {
                for (part in line.parts) {
                    if (part !is PostPart.Ref || part.postNo !in present || part.postNo == post.no) continue
                    val list = out.getOrPut(part.postNo) { mutableListOf() }
                    if (post.no !in list) list.add(post.no)
                }
            }
        }
        return out
    }

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
