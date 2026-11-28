package com.orbin.core.model.stream

/** One thing the controls pop-up can do. [keywords] let "reload" find "Refresh everything". */
data class PaletteCommand(
    val id: String,
    val label: String,
    val keywords: List<String> = emptyList(),
)

/** What the person typed: `/theme` is a command search; anything else filters the current view. */
sealed interface PaletteInput {
    data class Command(
        val query: String,
    ) : PaletteInput

    data class Filter(
        val query: String,
    ) : PaletteInput
}

/**
 * Small, explicit fuzzy ranking for the command line. Order of preference: label prefix, word
 * prefix, substring, then in-order letters. A keyword match counts for half, so a label match
 * always wins over a keyword match of the same kind.
 */
object CommandRanking {
    const val KEYWORD_WEIGHT = 0.5
    const val DEFAULT_LIMIT = 8

    private const val PREFIX = 100.0
    private const val WORD_PREFIX = 80.0
    private const val SUBSTRING = 60.0
    private const val SUBSEQUENCE = 30.0
    private const val LENGTH_CAP = 50
    private val NON_WORD = Regex("[^a-z0-9]+")

    /** 0 means no match; higher is better. A blank [query] matches everything equally (1). */
    fun scoreText(
        text: String,
        query: String?,
    ): Double {
        val q = query.orEmpty().trim().lowercase()
        if (q.isEmpty()) return 1.0
        val t = text.lowercase()
        return when {
            t.startsWith(q) -> PREFIX - minOf(t.length, LENGTH_CAP) / 100.0
            t.split(NON_WORD).any { it.startsWith(q) } -> WORD_PREFIX
            t.contains(q) -> SUBSTRING
            isSubsequence(q, t) -> SUBSEQUENCE
            else -> 0.0
        }
    }

    /** Best matches first; ties keep their original order so the list never reshuffles. */
    fun rank(
        commands: List<PaletteCommand>,
        query: String?,
        limit: Int = DEFAULT_LIMIT,
    ): List<PaletteCommand> {
        val q = query.orEmpty().trim()
        val ranked = commands.mapIndexed { index, command -> rankOf(command, index, q) }
        val matching = ranked.filter { it.score > 0.0 }
        val ordered = matching.sortedWith(compareByDescending<Ranked> { it.score }.thenBy { it.index })
        return ordered.take(limit.coerceAtLeast(0)).map { it.command }
    }

    private fun rankOf(
        command: PaletteCommand,
        index: Int,
        query: String,
    ): Ranked {
        val keyword = command.keywords.maxOfOrNull { scoreText(it, query) * KEYWORD_WEIGHT } ?: 0.0
        return Ranked(command, maxOf(scoreText(command.label, query), keyword), index)
    }

    fun parseInput(text: String?): PaletteInput {
        val raw = text.orEmpty()
        val trimmed = raw.trimStart()
        return if (trimmed.startsWith('/')) {
            PaletteInput.Command(trimmed.drop(1).trim())
        } else {
            PaletteInput.Filter(raw.trim())
        }
    }

    private fun isSubsequence(
        query: String,
        text: String,
    ): Boolean {
        var matched = 0
        for (ch in text) {
            if (matched < query.length && ch == query[matched]) matched++
        }
        return matched == query.length
    }

    private class Ranked(
        val command: PaletteCommand,
        val score: Double,
        val index: Int,
    )
}
