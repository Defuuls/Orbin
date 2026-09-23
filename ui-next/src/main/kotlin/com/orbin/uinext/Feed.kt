package com.orbin.uinext

enum class FeedLayout {
    LIST,
    GRID,
    IMAGES,
}

/**
 * A feed/catalog row. Production callers must pass a thread-stable [id]
 * (`"board/thread"`); the `"$board:$subject"` default is only for previews/tests.
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
    val threadNumber: String =
        id.substringAfterLast('/').trimStart(':').takeIf { candidate ->
            candidate.isNotEmpty() && candidate.all { it.isDigit() }
        } ?: "",
)
