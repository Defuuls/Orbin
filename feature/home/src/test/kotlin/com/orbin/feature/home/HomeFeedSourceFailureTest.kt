package com.orbin.feature.home

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.HomeFeedMode
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class HomeFeedSourceFailureTest {
    @Test
    fun `an empty healthy source cannot mask a followed source outage`() {
        val result =
            mergeProviderFeeds(
                listOf(
                    SubscribedFeedUiState.Error("Source unavailable"),
                    SubscribedFeedUiState.Success(persistentListOf()),
                ),
            )
        assertThat(result).isEqualTo(SubscribedFeedUiState.Error("Source unavailable"))
    }

    @Test
    fun `non-video catalogs retain the other source failure instead of implying no videos exist`() {
        val result = merged(MediaType.IMAGE)
        assertThat(
            result.boards.flatMap { it.threads }.flatMap { it.originalPost.attachments }.none {
                it.type ==
                    MediaType.VIDEO
            },
        ).isTrue()
        assertThat(result.errorMessage).isEqualTo("Source unavailable")
        assertThat(result.stale).isTrue()
        // The adapter offers Retry for an empty mapped video page and does not auto-advance.
        assertThat(result.canLoadMore).isTrue()
    }

    @Test
    fun `a partial source outage preserves healthy video rows and their next page`() {
        val result = merged(MediaType.VIDEO)
        assertThat(
            result.boards
                .single()
                .threads
                .single()
                .originalPost.attachments
                .single()
                .type,
        ).isEqualTo(MediaType.VIDEO)
        assertThat(result.errorMessage).isEqualTo("Source unavailable")
        assertThat(result.canLoadMore).isTrue()
    }

    private fun merged(type: MediaType): SubscribedFeedUiState.Success {
        val board = BoardId("g")
        val thread =
            CatalogThread(
                ThreadKey(ProviderId("bbwchan"), board, ThreadId(1)),
                Post(
                    id = PostId(1),
                    board = board,
                    threadId = ThreadId(1),
                    isOriginalPost = true,
                    attachments =
                        persistentListOf(
                            MediaAttachment("file", "file", "mp4", type, "https://example.org/file", ""),
                        ),
                ),
                ThreadStats(),
            )
        return mergeProviderFeeds(
            listOf(
                SubscribedFeedUiState.Error("Source unavailable"),
                SubscribedFeedUiState.Success(
                    persistentListOf(
                        SubscribedBoardFeed(Board(board, "General"), persistentListOf(thread), null, "bbwchan"),
                    ),
                    canLoadMore = true,
                    mode = HomeFeedMode.FOR_YOU,
                ),
            ),
        ) as SubscribedFeedUiState.Success
    }
}
