package com.orbin.data.sync

import kotlinx.serialization.Serializable

/**
 * What Orbin keeps in the sync file: every visited thread (the feed's read markers, and where in
 * the thread reading stopped) and every bookmark (with whether it is watched). Android and iOS
 * write the same file, so a field added here must be optional, with a default, for older builds
 * to keep reading it.
 */
@Serializable
data class SyncDocument(
    val version: Int = SYNC_FORMAT_VERSION,
    val visits: List<SyncedVisit> = emptyList(),
    val bookmarks: List<SyncedBookmark> = emptyList(),
)

/** A visited thread: it shows as read in the feed, and opens at [lastReadPostId]. */
@Serializable
data class SyncedVisit(
    val provider: String,
    val board: String,
    val thread: Long,
    val title: String,
    val thumbnailUrl: String? = null,
    val lastVisitedMillis: Long,
    val lastReadPostId: Long? = null,
) {
    val key: SyncKey get() = SyncKey(provider, board, thread)
}

/** A bookmarked thread, [watched] when its new replies are followed. */
@Serializable
data class SyncedBookmark(
    val provider: String,
    val board: String,
    val thread: Long,
    val title: String,
    val thumbnailUrl: String? = null,
    val createdAtMillis: Long,
    val watched: Boolean,
    val lastSeenReplyCount: Int = 0,
) {
    val key: SyncKey get() = SyncKey(provider, board, thread)
}

/** A thread's identity across devices, as the database keys it. */
data class SyncKey(
    val provider: String,
    val board: String,
    val thread: Long,
)

/**
 * The three-way merge of this device's state ([local]) with the sync file ([remote]), against
 * what both last agreed on ([base], null before the first sync). Comparing with [base] is what
 * tells a deletion from something never seen: a thread missing here but in [base] was removed
 * here, so it goes; one in neither [base] nor here is new from elsewhere, so it comes. A change
 * beats a removal, so nothing edited on one device is lost because the other removed it.
 *
 * - Visits: the more recent visit wins, with the post it stopped at.
 * - Bookmarks: watching follows whichever side changed it (this device if both did); the replies
 *   already seen is the larger count; the earlier creation time is kept.
 */
fun mergeSync(
    base: SyncDocument?,
    local: SyncDocument,
    remote: SyncDocument?,
): SyncDocument {
    val remoteDoc = remote ?: SyncDocument()
    return SyncDocument(
        visits =
            mergeRows(base?.visits.orEmpty(), local.visits, remoteDoc.visits, SyncedVisit::key) { _, mine, theirs ->
                if (theirs.lastVisitedMillis > mine.lastVisitedMillis) theirs else mine
            }.sortedByDescending { it.lastVisitedMillis },
        bookmarks =
            mergeRows(
                base?.bookmarks.orEmpty(),
                local.bookmarks,
                remoteDoc.bookmarks,
                SyncedBookmark::key,
            ) { was, mine, theirs ->
                val watched =
                    when {
                        was == null -> mine.watched || theirs.watched
                        mine.watched != was.watched -> mine.watched
                        else -> theirs.watched
                    }
                mine.copy(
                    watched = watched,
                    lastSeenReplyCount = maxOf(mine.lastSeenReplyCount, theirs.lastSeenReplyCount),
                    createdAtMillis = minOf(mine.createdAtMillis, theirs.createdAtMillis),
                )
            }.sortedByDescending { it.createdAtMillis },
    )
}

private fun <T> mergeRows(
    base: List<T>,
    local: List<T>,
    remote: List<T>,
    key: (T) -> SyncKey,
    combine: (base: T?, local: T, remote: T) -> T,
): List<T> {
    val was = base.associateBy(key)
    val mine = local.associateBy(key)
    val theirs = remote.associateBy(key)
    return (mine.keys + theirs.keys).mapNotNull { id ->
        val here = mine[id]
        val there = theirs[id]
        val before = was[id]
        when {
            here != null && there != null -> combine(before, here, there)
            // Only here: removed elsewhere if it was agreed before and is unchanged since.
            here != null -> here.takeUnless { before == here }
            // Only there: removed here on the same terms.
            else -> there.takeUnless { before == there }
        }
    }
}

const val SYNC_FORMAT_VERSION = 1
