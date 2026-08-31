package com.orbin.feature.thread

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PosterInfo
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

/**
 * The everyday-shock-words setting reaches the replies a reader is actually looking at.
 *
 * It was wired to the board catalog alone, so turning it on filtered the catalog you browsed and
 * left the thread you opened from it untouched — which is where the words are read. A setting that
 * works on one of the three surfaces it names reads as broken rather than as scoped.
 */
class HarshFilterReachTest {
    @Test
    fun `an opted-in reader loses the harsh reply`() {
        val filtered = thread().hidingMatches(tokens = emptySet(), includeHarsh = true)
        assertThat(filtered.replies.map { it.id.value }).containsExactly(2L)
    }

    @Test
    fun `a reader who has not opted in keeps it`() {
        // The always-on filter is unchanged by the setting: this reply is ordinary discussion, and
        // only the opt-in list of everyday words catches it.
        val filtered = thread().hidingMatches(tokens = emptySet(), includeHarsh = false)
        assertThat(filtered.replies.map { it.id.value }).containsExactly(1L, 2L)
    }

    @Test
    fun `the reader's own hidden keywords still apply either way`() {
        listOf(true, false).forEach { harsh ->
            val filtered = thread().hidingMatches(tokens = setOf("kettles"), includeHarsh = harsh)
            assertThat(filtered.replies.map { it.id.value }).doesNotContain(2L)
        }
    }

    private fun thread(): Thread {
        val key = ThreadKey(ProviderId("p"), BoardId("g"), ThreadId(1))
        return Thread(
            key = key,
            originalPost = post(id = 0, comment = "A thread about kettles"),
            replies =
                listOf(
                    // Caught only when the reader has opted in.
                    post(id = 1, comment = "a thread about murder"),
                    post(id = 2, comment = "more on kettles"),
                ).toImmutableList(),
            stats = ThreadStats(replyCount = 2, imageCount = 0),
        )
    }

    private fun post(
        id: Long,
        comment: String,
    ) = Post(
        id = PostId(id),
        board = BoardId("g"),
        threadId = ThreadId(1),
        isOriginalPost = id == 0L,
        comment = PostComment(raw = comment, nodes = persistentListOf()),
        poster = PosterInfo(),
        attachments = persistentListOf(),
    )
}
