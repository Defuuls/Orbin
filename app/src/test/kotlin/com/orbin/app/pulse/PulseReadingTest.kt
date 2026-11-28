package com.orbin.app.pulse

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.uipulse.PulseLineUi
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

class PulseReadingTest {
    private fun post(
        id: Long,
        original: Boolean = false,
    ) = Post(
        id = PostId(id),
        board = BoardId("g"),
        threadId = ThreadId(1L),
        isOriginalPost = original,
    )

    private val thread =
        Thread(
            key = ThreadKey(ProviderId("p"), BoardId("g"), ThreadId(1L)),
            originalPost = post(1, original = true),
            replies = listOf(post(2), post(3), post(4)).toImmutableList(),
        )

    @Test
    fun repliesNotYetSeenAreUnreadAndTheOpeningPostNever() {
        assertThat(unreadPosts(thread, seen = setOf(2L))).containsExactly(3L, 4L)
        assertThat(unreadPosts(thread, seen = emptySet())).containsExactly(2L, 3L, 4L)
    }

    @Test
    fun aThreadFullySeenHasNothingUnread() {
        assertThat(unreadPosts(thread, seen = setOf(1L, 2L, 3L, 4L, 99L))).isEmpty()
    }

    @Test
    fun onlyPostLinesYieldNumbersForTheSeenTracker() {
        assertThat(visiblePostNumbers(listOf("p:5", "t:a|g|1", "p:x", "p:12", "")))
            .containsExactly("5", "12")
        assertThat(visiblePostNumbers(emptyList())).isEmpty()
    }

    @Test
    fun markingUnreadChangesOnlyThoseLines() {
        val lines =
            listOf(
                PulseLineUi("p:1", "", "", "", number = 1L),
                PulseLineUi("p:2", "", "", "", number = 2L),
                PulseLineUi("t:a|g|1", "", "", ""),
            )
        val marked = markUnread(lines, setOf(2L))
        assertThat(marked.map { it.unread }).containsExactly(false, true, false).inOrder()
        assertThat(markUnread(lines, emptySet())).isSameInstanceAs(lines)
    }
}
