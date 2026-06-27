package com.orbin.core.model

/**
 * A board exposed by a provider. Boards are the top-level browsable unit (e.g. /g/ - Technology).
 *
 * Fields beyond [id]/[title] are optional because not every engine exposes the same metadata;
 * a provider should populate what it can and leave the rest as defaults.
 */
data class Board(
    val id: BoardId,
    val title: String,
    val description: String = "",
    val category: String = "",
    val isNsfw: Boolean = false,
    /** Number of catalog pages, when known. Drives pagination hints. */
    val pageCount: Int? = null,
    /** Posts after which a thread stops bumping. */
    val bumpLimit: Int? = null,
    /** Image count after which no more images may be posted. */
    val imageLimit: Int? = null,
    /** Maximum allowed comment length, if advertised by the engine. */
    val maxCommentChars: Int? = null,
    /** Whether the board supports posting media at all. */
    val supportsMedia: Boolean = true,
)
