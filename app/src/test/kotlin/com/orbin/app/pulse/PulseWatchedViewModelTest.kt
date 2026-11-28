package com.orbin.app.pulse

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeBookmarkRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PulseWatchedViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun bookmark(
        no: Long,
        watched: Boolean,
        seen: Int,
        latest: Int,
    ) = Bookmark(
        key = ThreadKey(ProviderId("p"), BoardId("g"), ThreadId(no)),
        title = "t$no",
        createdAtMillis = 1L,
        isWatched = watched,
        lastSeenReplyCount = seen,
        latestReplyCount = latest,
    )

    @Test
    fun onlyWatchedThreadsWithNewRepliesNeedYou() =
        runTest {
            val repository =
                FakeBookmarkRepository(
                    listOf(
                        bookmark(1, watched = true, seen = 1, latest = 4),
                        bookmark(2, watched = true, seen = 4, latest = 4),
                        bookmark(3, watched = false, seen = 0, latest = 9),
                    ),
                )
            val viewModel = PulseWatchedViewModel(repository)
            val job = viewModel.needingYou.launchIn(backgroundScope)

            assertThat(viewModel.needingYou.first { it.isNotEmpty() }.map { it.key.thread.value }).containsExactly(1L)
            job.cancel()
        }

    @Test
    fun readingAThreadRemovesItFromWhatNeedsYou() =
        runTest {
            val repository = FakeBookmarkRepository(listOf(bookmark(1, watched = true, seen = 1, latest = 4)))
            val viewModel = PulseWatchedViewModel(repository)
            viewModel.needingYou.launchIn(backgroundScope)
            assertThat(viewModel.needingYou.first { it.isNotEmpty() }).hasSize(1)

            repository.markRead(ThreadKey(ProviderId("p"), BoardId("g"), ThreadId(1L)))

            assertThat(viewModel.needingYou.first { it.isEmpty() }).isEmpty()
        }
}
