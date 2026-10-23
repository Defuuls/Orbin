package com.orbin.feature.downloads

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.DownloadRecord
import com.orbin.core.model.DownloadStatus
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.domain.repository.DownloadRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val failed = DownloadRecord(id = 7, url = "https://i.example/a.webm", "a.webm", DownloadStatus.FAILED, 0)
    private val done = DownloadRecord(id = 8, url = "https://i.example/b.jpg", "b.jpg", DownloadStatus.COMPLETED, 0)
    private val repository = RecordingDownloads(listOf(failed, done))

    @Test
    fun `opening the screen refreshes statuses and lists every download`() =
        runTest {
            val viewModel = DownloadsViewModel(repository)
            advanceUntilIdle()

            assertThat(repository.refreshes).isEqualTo(1)
            assertThat(viewModel.downloads.first { it.isNotEmpty() }.map { it.id }).containsExactly(7L, 8L).inOrder()
        }

    @Test
    fun `retry asks for the failed download again`() =
        runTest {
            val viewModel = DownloadsViewModel(repository)

            viewModel.retry(7)
            advanceUntilIdle()

            assertThat(repository.retried).containsExactly(7L)
        }

    @Test
    fun `clearing empties the list`() =
        runTest {
            val viewModel = DownloadsViewModel(repository)
            viewModel.downloads.first { it.isNotEmpty() }

            viewModel.clear()
            advanceUntilIdle()

            assertThat(viewModel.downloads.first { it.isEmpty() }).isEmpty()
        }

    private class RecordingDownloads(
        initial: List<DownloadRecord>,
    ) : DownloadRepository {
        private val state = MutableStateFlow(initial)
        var refreshes = 0
        val retried = mutableListOf<Long>()

        override fun observeDownloads(): Flow<List<DownloadRecord>> = state

        override suspend fun enqueue(
            url: String,
            fileName: String,
            boardId: String?,
            threadId: Long?,
            threadTitle: String?,
        ): Long = 0L

        override suspend fun refreshStatuses() {
            refreshes++
        }

        override suspend fun clearHistory() {
            state.value = emptyList()
        }

        override suspend fun retry(id: Long): Long {
            retried += id
            return id
        }

        override suspend fun writeTextFile(
            fileName: String,
            content: String,
        ): Boolean = true
    }
}
