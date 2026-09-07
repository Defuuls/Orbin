package com.orbin.uinext

enum class FeedLayout {
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
)
