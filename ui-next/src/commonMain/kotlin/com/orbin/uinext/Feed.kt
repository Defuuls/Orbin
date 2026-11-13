package com.orbin.uinext

/**
 * A feed/catalog row. Production callers must pass a thread-stable [id]
 * (`"provider/board/thread"`); the `"$board:$subject"` default is only for previews/tests.
 */
data class FeedRow(
    val subject: String,
    val board: String,
    val activity: String,
    val replies: Int,
    val media: Int,
    val hasPreview: Boolean = true,
    val read: Boolean = false,
    val unread: Int = 0,
    val id: String = "$board:$subject",
    val muted: Boolean = false,
    val excerpt: String = "",
    val boardTitle: String = board,
    /**
     * Width/height of the preview media, or 0 when unknown. Grid tiles size themselves to it so the
     * whole image shows instead of a centre crop.
     */
    val mediaAspectRatio: Float = 0f,
    /** Human-readable source name, supplied by the platform adapter (for example 4chan). */
    val site: String = "",
    val poster: String = "Anonymous",
    /** File extension or media kind; omitted for text-only posts. */
    val mediaLabel: String = "",
    val watched: Boolean = false,
    val bookmarked: Boolean = false,
    val sticky: Boolean = false,
    val closed: Boolean = false,
    val imageSpoiler: Boolean = false,
    val threadNumber: String =
        id.substringAfterLast('/').trimStart(':').takeIf { candidate ->
            candidate.isNotEmpty() && candidate.all { it.isDigit() }
        } ?: "",
) {
    /**
     * Quote-aware excerpt lines, computed once when the row is built so catalog
     * and feed composition only apply theme colors.
     */
    val previewLines: List<PreviewLine> = previewLinesOf(excerpt)
}

/** One visible excerpt line, with [quoted] true for greentext. */
data class PreviewLine(
    val text: String,
    val quoted: Boolean,
)

internal fun previewLinesOf(excerpt: String): List<PreviewLine> =
    excerpt
        .lineSequence()
        .filter { it.isNotBlank() }
        .map { line ->
            PreviewLine(text = line.trim(), quoted = line.trimStart().startsWith('>'))
        }.toList()
