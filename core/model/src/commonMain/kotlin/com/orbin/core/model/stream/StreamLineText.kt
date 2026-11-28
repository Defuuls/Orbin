package com.orbin.core.model.stream

/** Where a thread line points. Provider and board are the engine's own ids, never display names. */
data class ThreadRef(
    val provider: String,
    val board: String,
    val thread: Long,
)

/**
 * Line ids for the stream. The id of a line is the only thing a tap hands back to the host, so
 * encoding and decoding live together and decoding never trusts its input.
 */
object StreamIds {
    private const val THREAD_PREFIX = "t:"
    private const val POST_PREFIX = "p:"
    private const val SEPARATOR = '|'
    private const val THREAD_PARTS = 3

    /** `null` when a part would make the id ambiguous, so such a thread simply has no line. */
    fun thread(
        provider: String,
        board: String,
        thread: Long,
    ): String? {
        val named = provider.isNotEmpty() && board.isNotEmpty() && thread > 0L
        val unambiguous = SEPARATOR !in provider && SEPARATOR !in board
        return if (named && unambiguous) "$THREAD_PREFIX$provider$SEPARATOR$board$SEPARATOR$thread" else null
    }

    fun post(postNo: Long): String = "$POST_PREFIX$postNo"

    /** The post number in a post line's id, or `null` for any other id. */
    fun parsePost(id: String?): Long? {
        val digits = id?.takeIf { it.startsWith(POST_PREFIX) }?.removePrefix(POST_PREFIX)
        val number = digits?.toLongOrNull()
        return number?.takeIf { it > 0L }
    }

    fun parseThread(id: String?): ThreadRef? {
        val parts = id?.takeIf { it.startsWith(THREAD_PREFIX) }?.removePrefix(THREAD_PREFIX)?.split(SEPARATOR)
        val thread = parts?.takeIf { it.size == THREAD_PARTS }?.get(2)?.toLongOrNull()
        return when {
            parts == null || thread == null -> null
            thread <= 0L -> null
            parts[0].isEmpty() || parts[1].isEmpty() -> null
            else -> ThreadRef(parts[0], parts[1], thread)
        }
    }
}

/** Short display strings for stream lines. Pure, so every edge is testable without a UI. */
object StreamText {
    private const val SECOND = 1_000L
    private const val MINUTE = 60 * SECOND
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val WEEK = 7 * DAY
    private const val ELLIPSIS = "…"
    private val WHITESPACE = Regex("\\s+")

    /**
     * How long ago, in the stream's compact form: `now`, `5m`, `3h`, `2d`, `6w`. An unknown time
     * (zero or negative) is blank rather than a misleading `now`; a time in the future reads as
     * `now`, since clocks disagree.
     */
    fun age(
        nowMillis: Long,
        thenMillis: Long,
    ): String {
        val elapsed = nowMillis - thenMillis
        return when {
            thenMillis <= 0L -> ""
            elapsed < MINUTE -> "now"
            elapsed < HOUR -> "${elapsed / MINUTE}m"
            elapsed < DAY -> "${elapsed / HOUR}h"
            elapsed < WEEK -> "${elapsed / DAY}d"
            else -> "${elapsed / WEEK}w"
        }
    }

    /** One line of [text]: whitespace runs collapsed, at most [max] chars, never ending mid-pair. */
    fun clip(
        text: String?,
        max: Int,
    ): String {
        val flat = text.orEmpty().trim().replace(WHITESPACE, " ")
        if (max <= 0) return ""
        if (flat.length <= max) return flat
        var end = max - 1
        if (end > 0 && flat[end - 1].isHighSurrogate()) end--
        return flat.take(end.coerceAtLeast(0)).trimEnd() + ELLIPSIS
    }

    /** The subject if there is one, else the first non-blank line of the body, else `No.<n>`. */
    fun headline(
        subject: String?,
        body: String?,
        fallbackNo: Long,
        max: Int,
    ): String {
        val firstLine = body.orEmpty().lineSequence().firstOrNull { it.isNotBlank() }
        val chosen = subject?.takeIf { it.isNotBlank() } ?: firstLine
        return clip(chosen, max).ifEmpty { "No.$fallbackNo" }
    }
}

/** Filtering the stream by what the person types in the controls. */
object StreamFilter {
    /** [items] whose [text] matches [query], in their original order. A blank query keeps all. */
    fun <T> apply(
        items: List<T>,
        query: String?,
        text: (T) -> String,
    ): List<T> = items.filter { CommandRanking.scoreText(text(it), query) > 0.0 }
}
