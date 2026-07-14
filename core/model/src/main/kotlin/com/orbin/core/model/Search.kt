package com.orbin.core.model

/** Scope a search runs against. Local scopes work offline; remote depends on provider support. */
enum class SearchScope {
    /** Search within already-loaded posts of the current thread. */
    CURRENT_THREAD,

    /** Search a board's catalog by subject/comment (client-side over the catalog). */
    BOARD_CATALOG,

    /** Server-side search, when the provider advertises [ProviderCapabilities.supportsSearch]. */
    REMOTE,
}

/** A search query with optional filters, designed to extend to server-side providers later. */
data class SearchQuery(
    val provider: ProviderId,
    val text: String,
    val scope: SearchScope,
    val board: BoardId? = null,
    val filters: SearchFilters = SearchFilters(),
)

/** Filters applied on top of a [SearchQuery]. Empty values mean "no constraint". */
data class SearchFilters(
    val mediaOnly: Boolean = false,
    val minReplies: Int? = null,
    val includeNsfw: Boolean = true,
    val contentTypes: Set<SearchContentType> = emptySet(),
)

/** Optional content buckets used by catalog search filters. Empty means all content types. */
enum class SearchContentType {
    POST,
    IMAGE,
    VIDEO,
    AUDIO,
    URL,
}

/** A single search hit. Kept generic so thread and catalog results share one type. */
data class SearchResult(
    val key: ThreadKey,
    val title: String,
    val snippet: String,
    val matchedPost: PostId,
    val thumbnailUrl: String? = null,
)

/** A saved search query for quick access to frequent searches. */
data class SavedSearch(
    val id: Long = 0,
    val text: String,
    val board: BoardId? = null,
    val filters: SearchFilters = SearchFilters(),
    val createdAtMillis: Long = System.currentTimeMillis(),
)
