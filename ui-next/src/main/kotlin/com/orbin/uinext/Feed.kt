package com.orbin.uinext

enum class FeedLayout {
    LIST,
    GRID,
    IMAGES,
}

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
