package com.orbin.domain.filter

import com.orbin.core.model.Board
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.Thread
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.core.model.matchesFilterTokens
import kotlinx.collections.immutable.toImmutableList

/**
 * The always-on content filter, applied the same way wherever boards, catalogs and threads are
 * shown on Android and iOS: boards and catalog threads the filter catches are left out, a thread it
 * catches is not shown at all, and in any other thread the replies it catches are hidden.
 */
object ContentPolicy {
    fun visibleBoards(boards: List<Board>): List<Board> = boards.filterNot { it.isPermanentlyFiltered() }

    fun visibleCatalog(threads: List<CatalogThread>): List<CatalogThread> =
        threads.filterNot { it.isPermanentlyFiltered() }

    /** [thread] with the replies the filter catches removed, or null when the whole thread is caught. */
    fun visibleThread(thread: Thread): Thread? =
        if (thread.isPermanentlyFiltered()) {
            null
        } else {
            thread.copy(replies = thread.replies.filterNot { it.matchesFilterTokens(emptySet()) }.toImmutableList())
        }

    /** What a reader sees in place of a thread the filter catches. */
    const val BLOCKED_THREAD_MESSAGE = "This thread is caught by the built-in content filter."
}
