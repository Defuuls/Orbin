package com.orbin.core.model

import kotlinx.serialization.Serializable

/** How a board catalog should be ordered. Not every engine supports every option. */
enum class CatalogSort {
    /** Most recently bumped first (default for most engines). */
    BUMP_ORDER,
    CREATION_DATE,
    REPLY_COUNT,
    IMAGE_COUNT,
    LAST_REPLY,
    /** Subject A–Z, then thread number. */
    SUBJECT,
    ;

    /** Short label for the catalog sort chip. */
    val label: String
        get() =
            when (this) {
                BUMP_ORDER -> "Bump"
                CREATION_DATE -> "Created"
                REPLY_COUNT -> "Replies"
                IMAGE_COUNT -> "Images"
                LAST_REPLY -> "Latest"
                SUBJECT -> "A-Z"
            }
}

/**
 * How the subscribed feed — threads from every board, one list — is ordered.
 *
 * Distinct from [CatalogSort] because a feed has a board axis the catalog does not: grouping or
 * sorting by `/board/` is the thing someone looking across subscriptions actually asks for.
 */
@Serializable
enum class FeedSort(
    val label: String,
) {
    /** Board code A–Z, then most recently active within that board. The default. */
    BOARD("Board"),
    ACTIVITY("Active"),
    REPLIES("Replies"),
    IMAGES("Images"),
    CREATED("Created"),
    TITLE("A-Z"),
}

/** When the thread last moved, falling back to when it was posted if the engine omitted a bump. */
fun CatalogThread.activityMillis(): Long =
    if (stats.lastModifiedMillis > 0L) stats.lastModifiedMillis else originalPost.createdAtMillis

fun CatalogThread.sortTitle(): String = originalPost.subject ?: "No.${key.thread.value}"

fun CatalogSort.comparator(): Comparator<CatalogThread> =
    when (this) {
        CatalogSort.CREATION_DATE ->
            compareByDescending<CatalogThread> { it.originalPost.createdAtMillis }
        CatalogSort.REPLY_COUNT ->
            compareByDescending<CatalogThread> { it.stats.replyCount }
        CatalogSort.IMAGE_COUNT ->
            compareByDescending<CatalogThread> { it.stats.imageCount }
        CatalogSort.SUBJECT ->
            compareBy<CatalogThread, String>(String.CASE_INSENSITIVE_ORDER) { it.sortTitle() }
                .thenBy { it.key.thread.value }
        CatalogSort.BUMP_ORDER, CatalogSort.LAST_REPLY ->
            compareByDescending<CatalogThread> { it.activityMillis() }
    }

fun FeedSort.comparator(): Comparator<CatalogThread> =
    when (this) {
        FeedSort.BOARD ->
            compareBy<CatalogThread, String>(String.CASE_INSENSITIVE_ORDER) { it.key.board.value }
                .thenByDescending { it.activityMillis() }
        FeedSort.ACTIVITY ->
            compareByDescending<CatalogThread> { it.activityMillis() }
        FeedSort.REPLIES ->
            compareByDescending<CatalogThread> { it.stats.replyCount }
                .thenByDescending { it.activityMillis() }
        FeedSort.IMAGES ->
            compareByDescending<CatalogThread> { it.stats.imageCount }
                .thenByDescending { it.activityMillis() }
        FeedSort.CREATED ->
            compareByDescending<CatalogThread> { it.originalPost.createdAtMillis }
        FeedSort.TITLE ->
            compareBy<CatalogThread, String>(String.CASE_INSENSITIVE_ORDER) { it.sortTitle() }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.key.board.value }
    }

/** A request for a page of a board catalog. */
data class CatalogRequest(
    val provider: ProviderId,
    val board: BoardId,
    val page: Int = 0,
    val sort: CatalogSort = CatalogSort.BUMP_ORDER,
)
