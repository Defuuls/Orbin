package com.orbin.minimal

import com.orbin.core.model.Board
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.feature.home.SubscribedBoardFeed

/** One row of the flat feed: a thread, and the board it came from. */
data class MinimalThread(
    val board: Board,
    val thread: CatalogThread,
) {
    val id: String get() = "${board.id.value}/${thread.key.thread.value}"

    /**
     * The opening post's first attachment, or null when the thread has none.
     *
     * Permanently-filtered files are skipped rather than shown, matching the media wall. The
     * thread-level filters have already run by the time a row exists, so this is the last gap a
     * file could come through.
     */
    val preview: MediaAttachment?
        get() = thread.originalPost.attachments.firstOrNull { !it.isPermanentlyFiltered() }

    /**
     * The subject if the thread has one, otherwise the start of the opening post.
     *
     * Empty when the thread has neither. The stand-in shown to a reader is a string resource, so
     * it is resolved in the UI rather than hard-coded in English here.
     */
    val title: String
        get() {
            val subject =
                thread.originalPost.subject
                    ?.trim()
                    .orEmpty()
            if (subject.isNotEmpty()) return subject
            val comment =
                thread.originalPost.comment.raw
                    .trim()
            return when {
                comment.length > TITLE_MAX_CHARS -> comment.take(TITLE_MAX_CHARS) + "…"
                else -> comment
            }
        }
}

/**
 * Collapses the per-board feeds into one stream, newest activity first.
 *
 * This is the whole difference between this app and the full client's subscribed feed. That one
 * keeps its boards as separate sections with headers, per-board thread limits and layout modes;
 * here there is one list and no notion of which board you are "in" — which is what makes the
 * board tag on each row load-bearing rather than decoration.
 *
 * Sorting is by last activity across every board at once, so a quiet board's thread that just
 * bumped appears above a busy board's stale one. Ties break on thread id so the order is total,
 * and a redraw cannot shuffle two threads that bumped in the same millisecond.
 */
internal fun List<SubscribedBoardFeed>.flattenToFeed(): List<MinimalThread> =
    flatMap { boardFeed -> boardFeed.threads.map { MinimalThread(boardFeed.board, it) } }
        .sortedWith(
            compareByDescending<MinimalThread> { it.thread.stats.lastModifiedMillis }
                .thenByDescending { it.thread.key.thread.value },
        )

private const val TITLE_MAX_CHARS = 90
