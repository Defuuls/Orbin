package com.orbin.provider.lynxchan.api

import kotlinx.serialization.Serializable

/** `GET /boards.js?json=1` wraps the board list in a status/data envelope. */
@Serializable
data class LynxChanBoardsResponse(
    val status: String = "",
    val data: LynxChanBoardsData = LynxChanBoardsData(),
)

@Serializable
data class LynxChanBoardsData(
    val boards: List<LynxChanBoard> = emptyList(),
)

@Serializable
data class LynxChanBoard(
    val boardUri: String,
    val boardName: String = "",
    val boardDescription: String = "",
    val postsPerHour: Int = 0,
    val uniqueIps: Int? = null,
    val inactive: Boolean = false,
)

/**
 * `GET /{board}/catalog.json` returns a flat array of thread previews (unlike vichan's
 * paginated-pages shape). Notably has no `files` array - only a single [thumb]/[mime] pair, and no
 * poster name/id at all.
 */
@Serializable
data class LynxChanCatalogThread(
    val threadId: Long,
    val subject: String? = null,
    /** Rendered HTML - despite the name, this is the field to parse, not [message]. */
    val markdown: String? = null,
    val postCount: Int = 0,
    val fileCount: Int = 0,
    val locked: Boolean = false,
    val pinned: Boolean = false,
    val cyclic: Boolean = false,
    val autoSage: Boolean = false,
    /** ISO-8601 timestamp of the last bump. */
    val lastBump: String? = null,
    val thumb: String? = null,
    val mime: String? = null,
)

@Serializable
data class LynxChanFile(
    val originalName: String? = null,
    val path: String,
    val thumb: String? = null,
    val mime: String = "",
    val size: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class LynxChanPost(
    val postId: Long,
    val name: String? = null,
    /** Per-thread poster id hash; absent/null for boards without ID tracking. */
    val id: String? = null,
    val signedRole: String? = null,
    val subject: String? = null,
    val markdown: String? = null,
    /** ISO-8601 timestamp. */
    val creation: String? = null,
    val files: List<LynxChanFile> = emptyList(),
)

/** `GET /{board}/res/{threadId}.json`. The OP's fields live at the top level; [posts] holds only replies. */
@Serializable
data class LynxChanThreadResponse(
    val threadId: Long,
    val boardUri: String? = null,
    val subject: String? = null,
    val name: String? = null,
    val id: String? = null,
    val signedRole: String? = null,
    val markdown: String? = null,
    val creation: String? = null,
    val locked: Boolean = false,
    val archived: Boolean = false,
    val pinned: Boolean = false,
    val cyclic: Boolean = false,
    val autoSage: Boolean = false,
    val files: List<LynxChanFile> = emptyList(),
    val posts: List<LynxChanPost> = emptyList(),
    /** Only present on the full thread response, not the catalog. */
    val uniquePosters: Int? = null,
)
