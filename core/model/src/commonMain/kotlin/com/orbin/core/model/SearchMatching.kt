package com.orbin.core.model

/**
 * Whether this catalog thread answers [query]: its text in the opening post's subject or comment,
 * ignoring case, and every filter satisfied. A blank query matches everything.
 *
 * This is the catalog search both apps run, over the catalogs of the boards the reader follows.
 * Callers drop permanently filtered threads first, so a search is never a way to reach them.
 */
fun CatalogThread.matchesSearch(query: SearchQuery): Boolean {
    val needle = query.text.trim().lowercase()
    if (needle.isEmpty()) return true
    val subject = originalPost.subject?.lowercase().orEmpty()
    val comment = originalPost.comment.raw.lowercase()
    val filters = query.filters
    val textMatches = needle in subject || needle in comment
    val mediaMatches = !filters.mediaOnly || originalPost.attachments.isNotEmpty()
    val contentMatches = filters.contentTypes.isEmpty() || filters.contentTypes.any(::hasContent)
    return textMatches && mediaMatches && contentMatches
}

/** This thread as a search hit: its subject (or board), the start of its opening post, its thumbnail. */
fun CatalogThread.toSearchResult(): SearchResult =
    SearchResult(
        key = key,
        title = originalPost.subject ?: "/${key.board.value}/",
        snippet = originalPost.comment.raw.take(SEARCH_SNIPPET_MAX),
        matchedPost = originalPost.id,
        thumbnailUrl = originalPost.attachments.firstOrNull()?.thumbnailUrl,
    )

private fun CatalogThread.hasContent(type: SearchContentType): Boolean =
    when (type) {
        SearchContentType.POST -> true
        SearchContentType.IMAGE ->
            originalPost.attachments.any { it.type == MediaType.IMAGE || it.type == MediaType.ANIMATED_IMAGE }
        SearchContentType.VIDEO -> originalPost.attachments.any { it.type == MediaType.VIDEO }
        SearchContentType.AUDIO -> originalPost.attachments.any { it.type == MediaType.AUDIO }
        SearchContentType.URL -> URL_PATTERN.containsMatchIn(originalPost.comment.raw)
    }

private val URL_PATTERN = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

/** The most of a matching post's text a search result carries. */
private const val SEARCH_SNIPPET_MAX = 160
