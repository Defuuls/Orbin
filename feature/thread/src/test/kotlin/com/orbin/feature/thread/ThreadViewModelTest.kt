package com.orbin.feature.thread

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppSettings
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaFilter
import com.orbin.core.model.MediaType
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
import com.orbin.core.testing.repository.FakeSavedThreadRepository
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.repository.ThreadRepository
import com.orbin.domain.usecase.ObserveThreadUseCase
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private const val PROVIDER = "fourchan"
private const val BOARD = "g"
private const val THREAD = 1L

@OptIn(ExperimentalCoroutinesApi::class)
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
    fun `images-only leaves the posts in place and hides their videos`() =
        runTest {
            val settings = FakeSettingsRepository(AppSettings.Default.copy(mediaFilter = MediaFilter.IMAGES))
            val viewModel = createViewModel(thread = threadWithMixedMedia(), settingsRepository = settings)

            viewModel.uiState.test {
                var state = awaitItem()
                while (state !is ThreadUiState.Success) state = awaitItem()

                assertThat(
                    state.thread.originalPost.attachments
                        .map { it.id },
                ).containsExactly("jpg")
                assertThat(state.thread.replies).hasSize(1)
                assertThat(
                    state.thread.replies
                        .single()
                        .attachments,
                ).isEmpty()
            }
        }

    @Test
    fun `downloading all media downloads only what the filter shows`() =
        runTest {
            val settings = FakeSettingsRepository(AppSettings.Default.copy(mediaFilter = MediaFilter.VIDEOS))
            val downloads = FakeDownloadRepository()
            val viewModel =
                createViewModel(
                    thread = threadWithMixedMedia(),
                    settingsRepository = settings,
                    downloadRepository = downloads,
                )

            viewModel.uiState.test { awaitItem() }
            viewModel.downloadAllMedia()

            assertThat(downloads.enqueuedUrls)
                .containsExactly("https://example.org/webm", "https://example.org/mp4")
        }

    @Test
    fun `exporting links from a thread with none reports that instead of writing a file`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.test { awaitItem() }
            viewModel.exportLinks()

            assertThat(viewModel.exportMessage.value).isEqualTo("No links found in this thread")
        }

    /**
     * The reported bug: leave the app and come back (or restart the device) and the thread reopens
     * at the top. A cold ViewModel over the same history must surface where the reader left off.
     */
    @Test
    fun `a fresh view model resumes the scroll position saved by the previous one`() =
        runTest {
            val history = FakeHistoryRepository()

            val first = createViewModel(historyRepository = history)
            // Subscribing is what starts the load, and the load is what records the history row
            // that the scroll position is later written onto.
            first.uiState.test { awaitItem() }
            first.saveScrollPosition(PostId(42), offsetPx = 17)
            runCurrent()

            val reopened = createViewModel(historyRepository = history)
            reopened.initialScrollPosition.test {
                // The flow starts at null and fills in once history has been read.
                val restored = awaitItem() ?: awaitItem()
                assertThat(restored).isNotNull()
                assertThat(restored!!.postId).isEqualTo(PostId(42))
                assertThat(restored.offsetPx).isEqualTo(17)
            }
        }

    private fun createViewModel(
        thread: Thread = defaultThread(),
        bookmarkRepository: FakeBookmarkRepository = FakeBookmarkRepository(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
        downloadRepository: FakeDownloadRepository = FakeDownloadRepository(),
        historyRepository: FakeHistoryRepository = FakeHistoryRepository(),
    ) = ThreadViewModel(
        savedStateHandle =
            SavedStateHandle(
                mapOf("provider" to PROVIDER, "board" to BOARD, "thread" to THREAD, "title" to "Title"),
            ),
        observeThread = ObserveThreadUseCase(FakeThreadRepository(thread)),
        bookmarkRepository = bookmarkRepository,
        downloadRepository = downloadRepository,
        historyRepository = historyRepository,
        settingsRepository = settingsRepository,
        savedThreadRepository = FakeSavedThreadRepository(),
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

    /** An OP with one image and one video, and a reply carrying a second video. */
    private fun threadWithMixedMedia() =
        Thread(
            key = key,
            originalPost =
                Post(
                    id = PostId(THREAD),
                    board = BoardId(BOARD),
                    threadId = ThreadId(THREAD),
                    isOriginalPost = true,
                    attachments =
                        persistentListOf(
                            attachment("jpg", MediaType.IMAGE),
                            attachment("webm", MediaType.VIDEO),
                        ),
                ),
            replies =
                persistentListOf(
                    Post(
                        id = PostId(THREAD + 1),
                        board = BoardId(BOARD),
                        threadId = ThreadId(THREAD),
                        isOriginalPost = false,
                        attachments = persistentListOf(attachment("mp4", MediaType.VIDEO)),
                    ),
                ),
            stats = ThreadStats(),
        )

    private fun attachment(
        id: String,
        type: MediaType,
    ) = MediaAttachment(
        id = id,
        originalFileName = "$id.file",
        extension = "file",
        type = type,
        sourceUrl = "https://example.org/$id",
        thumbnailUrl = "https://example.org/$id/thumb",
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
