package com.orbin.feature.board

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.AppSettings
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.CatalogSort
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.HistoryEntry
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.core.model.ThumbnailSize
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeBookmarkRepository
import com.orbin.core.testing.repository.FakeHistoryRepository
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private const val PROVIDER = "fourchan"
private const val BOARD = "g"

class BoardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `watchedThreadIds only counts bookmarks watched on this board`() =
        runTest {
            val bookmarks =
                FakeBookmarkRepository(
                    listOf(
                        bookmark(BOARD, 1L, watched = true),
                        bookmark(BOARD, 2L, watched = false),
                        // Same thread id, different board: must not leak in.
                        bookmark("v", 1L, watched = true),
                    ),
                )
            val viewModel = createViewModel(bookmarkRepository = bookmarks)

            viewModel.watchedThreadIds.test {
                assertThat(awaitItem()).containsExactly(1L)
            }
        }

    @Test
    fun `visitedThreadIds only counts history on this board`() =
        runTest {
            val history =
                FakeHistoryRepository(
                    listOf(
                        historyEntry(BOARD, 5L),
                        historyEntry("v", 5L),
                    ),
                )
            val viewModel = createViewModel(historyRepository = history)

            viewModel.visitedThreadIds.test {
                assertThat(awaitItem()).containsExactly(5L)
            }
        }

    @Test
    fun `thumbnailSize follows settings`() =
        runTest {
            val settings = FakeSettingsRepository(AppSettings.Default.copy(thumbnailSize = ThumbnailSize.LARGE))
            val viewModel = createViewModel(settingsRepository = settings)

            viewModel.thumbnailSize.test {
                assertThat(awaitItem()).isEqualTo(ThumbnailSize.LARGE)
            }
        }

    @Test
    fun `toggling subscription on an unwatched thread bookmarks and watches it`() =
        runTest {
            val bookmarks = FakeBookmarkRepository()
            val viewModel = createViewModel(bookmarkRepository = bookmarks)

            viewModel.toggleThreadSubscription(catalogThread(BOARD, 9L))

            bookmarks.observeBookmarks().test {
                val saved = awaitItem().single()
                assertThat(saved.key).isEqualTo(ThreadKey(ProviderId(PROVIDER), BoardId(BOARD), ThreadId(9L)))
                assertThat(saved.isWatched).isTrue()
            }
        }

    @Test
    fun `toggling subscription on a watched thread unwatches it`() =
        runTest {
            val bookmarks = FakeBookmarkRepository(listOf(bookmark(BOARD, 9L, watched = true)))
            val viewModel = createViewModel(bookmarkRepository = bookmarks)

            // watchedThreadIds must actually be collected before toggling, since it is what
            // toggleThreadSubscription reads to decide whether to watch or unwatch.
            viewModel.watchedThreadIds.test {
                assertThat(awaitItem()).containsExactly(9L)
                viewModel.toggleThreadSubscription(catalogThread(BOARD, 9L))
                assertThat(awaitItem()).isEmpty()
            }
        }

    private fun createViewModel(
        bookmarkRepository: FakeBookmarkRepository = FakeBookmarkRepository(),
        historyRepository: FakeHistoryRepository = FakeHistoryRepository(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
    ) = BoardViewModel(
        savedStateHandle = SavedStateHandle(mapOf("provider" to PROVIDER, "board" to BOARD, "title" to "Title")),
        catalogRepository = FakeCatalogRepository,
        bookmarkRepository = bookmarkRepository,
        historyRepository = historyRepository,
        settingsRepository = settingsRepository,
    )

    private fun bookmark(
        board: String,
        thread: Long,
        watched: Boolean,
    ) = Bookmark(
        key = ThreadKey(ProviderId(PROVIDER), BoardId(board), ThreadId(thread)),
        title = "Thread $thread",
        createdAtMillis = 1_000L,
        isWatched = watched,
    )

    private fun historyEntry(
        board: String,
        thread: Long,
    ) = HistoryEntry(
        key = ThreadKey(ProviderId(PROVIDER), BoardId(board), ThreadId(thread)),
        title = "Thread $thread",
        lastVisitedMillis = 1_000L,
    )

    private fun catalogThread(
        board: String,
        thread: Long,
    ) = CatalogThread(
        key = ThreadKey(ProviderId(PROVIDER), BoardId(board), ThreadId(thread)),
        originalPost =
            Post(
                id = PostId(thread),
                board = BoardId(board),
                threadId = ThreadId(thread),
                isOriginalPost = true,
            ),
        stats = ThreadStats(replyCount = 3),
    )
}

/** No test exercises paging content itself; BoardViewModel's own logic is in the derived flows above. */
private object FakeCatalogRepository : CatalogRepository {
    override fun catalogStream(
        provider: ProviderId,
        board: BoardId,
        sort: CatalogSort,
    ): Flow<PagingData<CatalogThread>> = flowOf(PagingData.empty())
}
