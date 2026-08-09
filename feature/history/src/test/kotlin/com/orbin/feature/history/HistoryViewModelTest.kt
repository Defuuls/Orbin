package com.orbin.feature.history

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeHistoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class HistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `history reflects the repository`() =
        runTest {
            val repository = FakeHistoryRepository(listOf(entry("g", 1L), entry("v", 2L)))
            val viewModel = HistoryViewModel(repository)

            viewModel.history.test {
                assertThat(awaitItem().map { it.title }).containsExactly("Thread 1", "Thread 2")
            }
        }

    @Test
    fun `clear empties the history`() =
        runTest {
            val repository = FakeHistoryRepository(listOf(entry("g", 1L)))
            val viewModel = HistoryViewModel(repository)

            viewModel.clear()

            viewModel.history.test {
                assertThat(awaitItem()).isEmpty()
            }
        }

    private fun entry(
        board: String,
        thread: Long,
    ) = HistoryEntry(
        key = ThreadKey(ProviderId("fourchan"), BoardId(board), ThreadId(thread)),
        title = "Thread $thread",
        lastVisitedMillis = 1_000L,
    )
}
