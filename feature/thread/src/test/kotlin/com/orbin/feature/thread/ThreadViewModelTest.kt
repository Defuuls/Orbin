package com.orbin.feature.thread

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppSettings
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.core.model.ThumbnailSize
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeBookmarkRepository
import com.orbin.core.testing.repository.FakeDownloadRepository
import com.orbin.core.testing.repository.FakeHistoryRepository
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.repository.ThreadRepository
import com.orbin.domain.usecase.ObserveThreadUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private const val PROVIDER = "fourchan"
private const val BOARD = "g"
private const val THREAD = 1L

class ThreadViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val key = ThreadKey(ProviderId(PROVIDER), BoardId(BOARD), ThreadId(THREAD))

    @Test
    fun `toggling bookmark on an unbookmarked thread saves it`() =
        runTest {
            val bookmarks = FakeBookmarkRepository()
            val viewModel = createViewModel(bookmarkRepository = bookmarks)

            viewModel.isBookmarked.test {
                assertThat(awaitItem()).isFalse()
                viewModel.toggleBookmark()
                assertThat(awaitItem()).isTrue()
            }
        }

    @Test
    fun `toggling bookmark on an already-bookmarked thread removes it`() =
        runTest {
            val bookmarks =
                FakeBookmarkRepository(listOf(Bookmark(key = key, title = "Thread", createdAtMillis = 1_000L)))
            val viewModel = createViewModel(bookmarkRepository = bookmarks)

            viewModel.isBookmarked.test {
                assertThat(awaitItem()).isTrue()
                viewModel.toggleBookmark()
                assertThat(awaitItem()).isFalse()
            }
        }

    @Test
    fun `thumbnailSize and mediaScrollEnabled follow settings`() =
        runTest {
            val settings =
                FakeSettingsRepository(
                    AppSettings.Default.copy(thumbnailSize = ThumbnailSize.LARGE, mediaScrollThreadView = false),
                )
            val viewModel = createViewModel(settingsRepository = settings)

            viewModel.thumbnailSize.test { assertThat(awaitItem()).isEqualTo(ThumbnailSize.LARGE) }
            viewModel.mediaScrollEnabled.test { assertThat(awaitItem()).isFalse() }
        }

    @Test
    fun `exporting links from a thread with none reports that instead of writing a file`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.test { awaitItem() }
            viewModel.exportLinks()

            assertThat(viewModel.exportMessage.value).isEqualTo("No links found in this thread")
        }

    private fun createViewModel(
        thread: Thread = defaultThread(),
        bookmarkRepository: FakeBookmarkRepository = FakeBookmarkRepository(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
    ) = ThreadViewModel(
        savedStateHandle =
            SavedStateHandle(
                mapOf("provider" to PROVIDER, "board" to BOARD, "thread" to THREAD, "title" to "Title"),
            ),
        observeThread = ObserveThreadUseCase(FakeThreadRepository(thread)),
        bookmarkRepository = bookmarkRepository,
        downloadRepository = FakeDownloadRepository(),
        historyRepository = FakeHistoryRepository(),
        settingsRepository = settingsRepository,
    )

    private fun defaultThread() =
        Thread(
            key = key,
            originalPost =
                Post(
                    id = PostId(THREAD),
                    board = BoardId(BOARD),
                    threadId = ThreadId(THREAD),
                    isOriginalPost = true,
                ),
            stats = ThreadStats(),
        )
}

private class FakeThreadRepository(
    private val thread: Thread,
) : ThreadRepository {
    override fun observeThread(
        key: ThreadKey,
        forceRefresh: Boolean,
    ): Flow<OrbinResult<Thread>> = flowOf(OrbinResult.Success(thread))

    override suspend fun refreshThread(
        provider: ProviderId,
        board: BoardId,
        thread: ThreadId,
        forceRefresh: Boolean,
    ): OrbinResult<Thread> = OrbinResult.Success(this.thread)
}
