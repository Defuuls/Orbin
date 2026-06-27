package com.orbin.core.model

/** How a board catalog should be ordered. Not every engine supports every option. */
enum class CatalogSort {
    /** Most recently bumped first (default for most engines). */
    BUMP_ORDER,
    CREATION_DATE,
    REPLY_COUNT,
    IMAGE_COUNT,
    LAST_REPLY,
}

/** A request for a page of a board catalog. */
data class CatalogRequest(
    val provider: ProviderId,
    val board: BoardId,
    val page: Int = 0,
    val sort: CatalogSort = CatalogSort.BUMP_ORDER,
)
