package com.orbin.feature.thread

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.result.DataError
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppSettings
import com.orbin.core.model.BoardId
import com.orbin.core.model.Bookmark
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeBookmarkRepository
import com.orbin.core.testing.repository.FakeDownloadRepository
import com.orbin.core.testing.repository.FakeHistoryRepository
import com.orbin.core.testing.repository.FakeSavedThreadRepository
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.bookmarks.collectionUnread
import com.orbin.domain.repository.InMemoryThreadMemoryRepository
import com.orbin.domain.repository.ThreadMemoryRepository
import com.orbin.domain.repository.ThreadRepository
import com.orbin.domain.usecase.ObserveThreadUseCase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

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
                FakeBookmarkRepository(
                    listOf(Bookmark(key = key, title = "Thread", createdAtMillis = 1_000L, isWatched = false)),
                )
            val viewModel = createViewModel(bookmarkRepository = bookmarks)

            viewModel.isBookmarked.test {
                assertThat(awaitItem()).isTrue()
                viewModel.toggleBookmark()
                assertThat(awaitItem()).isFalse()
            }
        }

    @Test
    fun `saving a thread does not start watching it`() =
        runTest {
            val bookmarks = FakeBookmarkRepository()
            val viewModel = createViewModel(bookmarkRepository = bookmarks)

            viewModel.toggleBookmark()
            runCurrent()

            assertThat(bookmarks.getBookmark(key)?.isWatched).isFalse()
        }

    @Test
    fun `removing a save cannot delete an active watch`() =
        runTest {
            val saved = Bookmark(key = key, title = "Watched", createdAtMillis = 1_000L, isWatched = true)
            val bookmarks = FakeBookmarkRepository(listOf(saved))
            val viewModel = createViewModel(bookmarkRepository = bookmarks)

            viewModel.toggleBookmark()
            runCurrent()

            assertThat(bookmarks.getBookmark(key)).isEqualTo(saved)
        }

    @Test
    fun `watching a saved-only thread preserves its bookmark and starts notifications`() =
        runTest {
            val saved = Bookmark(key = key, title = "Saved title", createdAtMillis = 1_000L, isWatched = false)
            val bookmarks = FakeBookmarkRepository(listOf(saved))
            val viewModel = createViewModel(bookmarkRepository = bookmarks)

            viewModel.isWatching.test {
                assertThat(awaitItem()).isFalse()
                viewModel.toggleWatch()
                assertThat(awaitItem()).isTrue()
                assertThat(bookmarks.getBookmark(key)).isEqualTo(saved.copy(isWatched = true))
            }
        }

    @Test
    fun `stopping notifications keeps the thread in saved items`() =
        runTest {
            val saved = Bookmark(key = key, title = "Saved title", createdAtMillis = 1_000L, isWatched = true)
            val bookmarks = FakeBookmarkRepository(listOf(saved))
            val viewModel = createViewModel(bookmarkRepository = bookmarks)

            viewModel.isWatching.test {
                assertThat(awaitItem()).isTrue()
                viewModel.toggleWatch()
                assertThat(awaitItem()).isFalse()
                assertThat(bookmarks.getBookmark(key)).isEqualTo(saved.copy(isWatched = false))
            }
        }

    @Test
    fun `downloading all media works when thread is shown from saved copy`() =
        runTest {
            val savedThread = threadWithMixedMedia()
            val downloads = FakeDownloadRepository()
            val viewModel =
                createViewModel(
                    threadRepository = FailingThreadRepository(),
                    savedThreadRepository = FakeSavedThreadRepository(mutableMapOf(key to savedThread)),
                    downloadRepository = downloads,
                )

            viewModel.uiState.test {
                var state = awaitItem()
                while (state !is ThreadUiState.Success) state = awaitItem()
                assertThat(state.fromSavedCopy).isTrue()
                viewModel.downloadAllMedia()
                viewModel.exportMessage.first { it != null }
                assertThat(downloads.enqueuedUrls)
                    .containsExactly(
                        "https://example.org/jpg",
                        "https://example.org/webm",
                        "https://example.org/mp4",
                    )
            }
        }

    @Test
    fun `offline reading registers copied posts without clearing newer live replies`() =
        runTest {
            val memories = InMemoryThreadMemoryRepository()
            val bookmarks = FakeBookmarkRepository()
            bookmarks.addBookmark(Bookmark(key, "Watched", createdAtMillis = 0, isWatched = true, latestReplyCount = 5))
            val replies =
                persistentListOf(
                    Post(PostId(THREAD + 1), BoardId(BOARD), ThreadId(THREAD), false),
                    Post(PostId(THREAD + 2), BoardId(BOARD), ThreadId(THREAD), false),
                )
            val saved = defaultThread().copy(replies = replies, stats = ThreadStats(replyCount = 2))
            val viewModel =
                createViewModel(
                    threadRepository = FailingThreadRepository(),
                    savedThreadRepository = FakeSavedThreadRepository(mutableMapOf(key to saved)),
                    bookmarkRepository = bookmarks,
                    memoryRepository = memories,
                )
            viewModel.uiState.test {
                assertThat(awaitSuccess().fromSavedCopy).isTrue()
                assertThat(memories.get(key).knownPostIds).containsExactly(THREAD, THREAD + 1, THREAD + 2)
                viewModel.markVisible(replies.mapTo(mutableSetOf()) { it.id.value.toString() })
                runCurrent()
                val bookmark = bookmarks.getBookmark(key)!!
                assertThat(bookmark.latestReplyCount).isEqualTo(5)
                assertThat(bookmark.lastSeenReplyCount).isEqualTo(0)
                assertThat(bookmark.collectionUnread(memories.get(key))).isEqualTo(3)
            }
        }

    @Test
    fun `offline copy applies current NSFW covers before its files enter presentation`() =
        runTest {
            val viewModel =
                createViewModel(
                    threadRepository = FailingThreadRepository(),
                    savedThreadRepository = FakeSavedThreadRepository(mutableMapOf(key to threadWithMixedMedia())),
                    settingsRepository = FakeSettingsRepository(AppSettings.Default.copy(coverNsfwMedia = true)),
                )
            viewModel.uiState.test {
                val saved = awaitSuccess()
                assertThat(saved.fromSavedCopy).isTrue()
                assertThat(saved.presentation.attachments).isNotEmpty()
                assertThat(saved.presentation.attachments.all { it.isSpoiler }).isTrue()
            }
        }

    @Test
    fun `downloading videos only queues video attachments`() =
        runTest {
            val savedThread = threadWithMixedMedia()
            val downloads = FakeDownloadRepository()
            val viewModel =
                createViewModel(
                    threadRepository = FailingThreadRepository(),
                    savedThreadRepository = FakeSavedThreadRepository(mutableMapOf(key to savedThread)),
                    downloadRepository = downloads,
                )

            viewModel.uiState.test {
                var state = awaitItem()
                while (state !is ThreadUiState.Success) state = awaitItem()
                viewModel.downloadAllMedia(videosOnly = true)
                viewModel.exportMessage.first { it != null }
                assertThat(downloads.enqueuedUrls)
                    .containsExactly(
                        "https://example.org/webm",
                        "https://example.org/mp4",
                    )
            }
        }

    @Test
    fun `exporting links from a thread with none reports that instead of writing a file`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.uiState.test { awaitSuccess() }
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
            first.uiState.test { awaitSuccess() }
            first.saveScrollPosition(PostId(42), offsetPx = 17)
            advanceTimeBy(650L)
            runCurrent()

            val reopened = createViewModel(historyRepository = history)
            reopened.initialScrollPosition.test {
                val restored = awaitItem() ?: awaitItem()
                assertThat(restored).isNotNull()
                assertThat(restored!!.postId).isEqualTo(PostId(42))
                assertThat(restored.offsetPx).isEqualTo(17)
            }
        }

    @Test
    fun `saving a scroll position moves the restore target with it`() =
        runTest {
            val history = FakeHistoryRepository()
            val viewModel = createViewModel(historyRepository = history)
            viewModel.uiState.test { awaitSuccess() }

            viewModel.saveScrollPosition(PostId(42), offsetPx = 17)
            runCurrent()

            val target = viewModel.initialScrollPosition.value
            assertThat(target).isNotNull()
            assertThat(target!!.postId).isEqualTo(PostId(42))
            assertThat(target.offsetPx).isEqualTo(17)
        }

    @Test
    fun `failed refresh keeps content and prepared snapshot then recovery clears the error`() =
        runTest {
            val repository = RefreshingThreadRepository(defaultThread())
            val viewModel = createViewModel(threadRepository = repository)
            viewModel.uiState.test {
                val initial = awaitSuccess()
                repository.next = OrbinResult.Failure(DataError.Offline())
                viewModel.refresh()
                val retained = awaitSuccess()
                assertThat(retained.thread).isEqualTo(initial.thread)
                assertThat(retained.presentation).isSameInstanceAs(initial.presentation)
                assertThat(retained.refreshFailure).isInstanceOf(DataError.Offline::class.java)
                assertThat(viewModel.isRefreshing.value).isFalse()

                val updated = defaultThread().copy(stats = ThreadStats(replyCount = 4))
                repository.next = OrbinResult.Success(updated)
                viewModel.refresh()
                val recovered = awaitSuccess()
                assertThat(recovered.thread).isEqualTo(updated)
                assertThat(recovered.refreshFailure).isNull()
                assertThat(recovered.presentation).isNotSameInstanceAs(initial.presentation)
            }
        }

    @Test
    fun `refresh cancels an older load before it can replace the new revision`() =
        runTest {
            val updated = defaultThread().copy(stats = ThreadStats(replyCount = 5))
            val repository = RefreshingThreadRepository(defaultThread(), firstDelayMillis = 1_000L)
            val viewModel = createViewModel(threadRepository = repository)
            viewModel.uiState.test {
                assertThat(awaitItem()).isEqualTo(ThreadUiState.Loading)
                runCurrent()
                repository.next = OrbinResult.Success(updated)
                viewModel.refresh()
                assertThat(awaitSuccess().thread).isEqualTo(updated)
                advanceTimeBy(2_000L)
                runCurrent()
                assertThat((viewModel.uiState.value as ThreadUiState.Success).thread).isEqualTo(updated)
                expectNoEvents()
            }
        }

    @Test
    fun `the actual host load filters and formats comments only in its worker context`() =
        runTest {
            val worker = MarkedWorkerDispatcher()
            val walks = AtomicInteger()
            val nodes = WorkerCheckedNodes(worker, walks)
            val source =
                defaultThread().let {
                    it.copy(originalPost = it.originalPost.copy(comment = PostComment("", nodes)))
                }
            val repository = RefreshingThreadRepository(source)
            val viewModel = createViewModel(threadRepository = repository, presentationDispatcher = worker)
            viewModel.uiState.test {
                val initial = awaitSuccess()
                assertThat(
                    initial.presentation.rowModels
                        .single()
                        .body,
                ).isEqualTo("A visible comment")
                assertThat(walks.get()).isGreaterThan(0)
            }
        }

    @Test
    fun `leaving cancels initial loading immediately including unread observer and visible return restarts it`() =
        runTest {
            val repository = CancellableThreadRepository()
            val viewModel =
                createViewModel(
                    threadRepository = repository,
                    presentationDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            val unread = backgroundScope.launch { viewModel.firstUnreadPostId.collect() }
            viewModel.uiState.test {
                assertThat(awaitItem()).isEqualTo(ThreadUiState.Loading)
                runCurrent()
                assertThat(repository.requests).hasSize(1)
                cancelAndIgnoreRemainingEvents()
            }
            unread.cancel()
            runCurrent()
            assertThat(repository.requests.single().cancelled).isTrue()

            lateinit var loaded: ThreadUiState.Success
            viewModel.uiState.test {
                assertThat(awaitItem()).isEqualTo(ThreadUiState.Loading)
                runCurrent()
                assertThat(repository.requests).hasSize(2)
                repository.requests
                    .last()
                    .result
                    .complete(OrbinResult.Success(defaultThread()))
                loaded = awaitSuccess()
            }
            runCurrent()
            viewModel.uiState.test {
                assertThat(awaitSuccess().presentation).isSameInstanceAs(loaded.presentation)
                runCurrent()
                assertThat(repository.requests).hasSize(2)
            }
            assertThat(repository.requests.map { it.forceRefresh }).containsExactly(false, false).inOrder()
        }

    @Test
    fun `leaving cancels refresh while retaining Ready and completed refresh never repeats on resume`() =
        runTest {
            val repository = CancellableThreadRepository()
            val viewModel =
                createViewModel(
                    threadRepository = repository,
                    presentationDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            lateinit var initial: ThreadUiState.Success
            viewModel.uiState.test {
                awaitItem()
                runCurrent()
                repository.requests
                    .single()
                    .result
                    .complete(OrbinResult.Success(defaultThread()))
                initial = awaitSuccess()
                viewModel.refresh()
                runCurrent()
                assertThat(repository.requests).hasSize(2)
                assertThat((viewModel.uiState.value as ThreadUiState.Success).presentation)
                    .isSameInstanceAs(initial.presentation)
            }
            runCurrent()
            assertThat(repository.requests.last().cancelled).isTrue()

            val updated = defaultThread().copy(stats = ThreadStats(replyCount = 7))
            lateinit var refreshed: ThreadUiState.Success
            viewModel.uiState.test {
                assertThat(awaitSuccess().presentation).isSameInstanceAs(initial.presentation)
                runCurrent()
                assertThat(repository.requests).hasSize(3)
                repository.requests
                    .last()
                    .result
                    .complete(OrbinResult.Success(updated))
                refreshed = awaitSuccess()
                assertThat(refreshed.thread).isEqualTo(updated)
            }
            runCurrent()
            viewModel.uiState.test {
                assertThat(awaitSuccess().presentation).isSameInstanceAs(refreshed.presentation)
                runCurrent()
                assertThat(repository.requests).hasSize(3)
            }
            assertThat(repository.requests.map { it.forceRefresh }).containsExactly(false, true, true).inOrder()
        }

    private suspend fun ReceiveTurbine<ThreadUiState>.awaitSuccess(): ThreadUiState.Success {
        var state = awaitItem()
        while (state !is ThreadUiState.Success) state = awaitItem()
        return state
    }

    @Test
    fun `visible reading and branch position survive a fresh view model without clearing unread on load`() =
        runTest {
            val memories = InMemoryThreadMemoryRepository()
            val bookmarks = FakeBookmarkRepository()
            bookmarks.addBookmark(Bookmark(key, "Watched", createdAtMillis = 0, isWatched = true, latestReplyCount = 1))
            val reply = Post(PostId(THREAD + 1), BoardId(BOARD), ThreadId(THREAD), false)
            val loaded = defaultThread().copy(replies = persistentListOf(reply), stats = ThreadStats(replyCount = 1))
            val first = createViewModel(thread = loaded, bookmarkRepository = bookmarks, memoryRepository = memories)
            val path = "$PROVIDER/$BOARD/$THREAD/${THREAD + 1}"
            first.uiState.test {
                awaitSuccess()
                runCurrent()
                assertThat(bookmarks.getBookmark(key)?.lastSeenReplyCount).isEqualTo(0)
                first.markVisible(setOf(reply.id.value.toString()))
                first.toggleFold(path)
                first.saveScrollPosition(reply.id, 23, path)
                advanceTimeBy(650)
                runCurrent()
                assertThat(memories.get(key).seenPostIds).contains(reply.id.value)
                assertThat(memories.get(key).foldedPaths).contains(path)
                assertThat(bookmarks.getBookmark(key)?.lastSeenReplyCount).isEqualTo(1)
            }
            val reopened = createViewModel(thread = loaded, memoryRepository = memories)
            reopened.initialScrollPosition.test {
                val position = awaitItem() ?: awaitItem()
                assertThat(position?.postId).isEqualTo(reply.id)
                assertThat(position?.offsetPx).isEqualTo(23)
            }
        }

    @Test
    fun `session-only seen and mark all read do not persist bookmark acknowledgements`() =
        runTest {
            val memories = InMemoryThreadMemoryRepository()
            val bookmarks = FakeBookmarkRepository()
            bookmarks.addBookmark(Bookmark(key, "Watched", createdAtMillis = 0, isWatched = true, latestReplyCount = 2))
            val replies =
                persistentListOf(
                    Post(PostId(THREAD + 1), BoardId(BOARD), ThreadId(THREAD), false),
                    Post(PostId(THREAD + 2), BoardId(BOARD), ThreadId(THREAD), false),
                )
            val viewModel =
                createViewModel(
                    thread = defaultThread().copy(replies = replies, stats = ThreadStats(replyCount = 2)),
                    bookmarkRepository = bookmarks,
                    settingsRepository = FakeSettingsRepository(AppSettings.Default.copy(historySessionOnly = true)),
                    memoryRepository = memories,
                )
            viewModel.uiState.test {
                awaitSuccess()
                runCurrent()
                viewModel.markVisible(replies.mapTo(mutableSetOf()) { it.id.value.toString() })
                runCurrent()
                assertThat(bookmarks.getBookmark(key)?.lastSeenReplyCount).isEqualTo(0)
                viewModel.markAllRead()
                runCurrent()
                assertThat(memories.get(key).acknowledgedReplyCount).isEqualTo(2)
                assertThat(bookmarks.getBookmark(key)?.lastSeenReplyCount).isEqualTo(0)
            }
        }

    @Test
    fun `foreground polling backs off idle threads and stops when its reader leaves`() =
        runTest {
            val requests = mutableListOf<Boolean>()
            val loaded = defaultThread()
            val repository =
                object : ThreadRepository {
                    override fun observeThread(
                        key: ThreadKey,
                        forceRefresh: Boolean,
                    ): Flow<OrbinResult<Thread>> =
                        flow {
                            requests += forceRefresh
                            emit(OrbinResult.Success(loaded))
                        }

                    override suspend fun refreshThread(
                        provider: ProviderId,
                        board: BoardId,
                        thread: ThreadId,
                        forceRefresh: Boolean,
                    ) = OrbinResult.Success(loaded)
                }
            val viewModel =
                createViewModel(
                    threadRepository = repository,
                    presentationDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            viewModel.uiState.test {
                awaitSuccess()
                val poll = backgroundScope.launch { viewModel.pollWhileVisible() }
                runCurrent()
                advanceTimeBy(8_000)
                runCurrent()
                assertThat(requests).hasSize(2)
                advanceTimeBy(15_999)
                runCurrent()
                assertThat(requests).hasSize(2)
                advanceTimeBy(1)
                runCurrent()
                assertThat(requests).hasSize(3)
                poll.cancel()
                advanceTimeBy(120_000)
                runCurrent()
                assertThat(requests).hasSize(3)
            }
        }

    private fun createViewModel(
        thread: Thread = defaultThread(),
        threadRepository: ThreadRepository = FakeThreadRepository(thread),
        bookmarkRepository: FakeBookmarkRepository = FakeBookmarkRepository(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
        downloadRepository: FakeDownloadRepository = FakeDownloadRepository(),
        historyRepository: FakeHistoryRepository = FakeHistoryRepository(),
        savedThreadRepository: FakeSavedThreadRepository = FakeSavedThreadRepository(),
        presentationDispatcher: CoroutineDispatcher = Dispatchers.Default,
        memoryRepository: ThreadMemoryRepository = InMemoryThreadMemoryRepository(),
    ) = ThreadViewModel(
        savedStateHandle =
            SavedStateHandle(
                mapOf("provider" to PROVIDER, "board" to BOARD, "thread" to THREAD, "title" to "Title"),
            ),
        observeThread = ObserveThreadUseCase(threadRepository),
        bookmarkRepository = bookmarkRepository,
        downloadRepository = downloadRepository,
        historyRepository = historyRepository,
        settingsRepository = settingsRepository,
        savedThreadRepository = savedThreadRepository,
        presentationDispatcher = presentationDispatcher,
        threadMemoryRepository = memoryRepository,
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

private class FailingThreadRepository : ThreadRepository {
    override fun observeThread(
        key: ThreadKey,
        forceRefresh: Boolean,
    ): Flow<OrbinResult<Thread>> = flowOf(OrbinResult.Failure(DataError.Offline()))

    override suspend fun refreshThread(
        provider: ProviderId,
        board: BoardId,
        thread: ThreadId,
        forceRefresh: Boolean,
    ): OrbinResult<Thread> = OrbinResult.Failure(DataError.Offline())
}

private class RefreshingThreadRepository(
    thread: Thread,
    private val firstDelayMillis: Long = 0L,
) : ThreadRepository {
    var next: OrbinResult<Thread> = OrbinResult.Success(thread)

    override fun observeThread(
        key: ThreadKey,
        forceRefresh: Boolean,
    ): Flow<OrbinResult<Thread>> {
        val result = next
        return flow {
            if (!forceRefresh) delay(firstDelayMillis)
            emit(result)
        }
    }

    override suspend fun refreshThread(
        provider: ProviderId,
        board: BoardId,
        thread: ThreadId,
        forceRefresh: Boolean,
    ): OrbinResult<Thread> = next
}

private class MarkedWorkerDispatcher :
    CoroutineDispatcher(),
    ThreadContextElement<Boolean> {
    private val active = ThreadLocal.withInitial { false }

    fun assertActive() = check(active.get() == true) { "The host walked post text outside its preparation worker" }

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) = Dispatchers.Default.dispatch(context, block)

    override fun updateThreadContext(context: CoroutineContext): Boolean =
        (active.get() == true).also { active.set(true) }

    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: Boolean,
    ) = active.set(oldState)
}

private class WorkerCheckedNodes(
    private val worker: MarkedWorkerDispatcher,
    private val walks: AtomicInteger,
) : ImmutableList<PostNode> by persistentListOf(PostNode.Text("A visible comment")) {
    override fun iterator(): Iterator<PostNode> {
        worker.assertActive()
        walks.incrementAndGet()
        return persistentListOf(PostNode.Text("A visible comment")).iterator()
    }
}

private class CancellableThreadRepository : ThreadRepository {
    data class Request(
        val forceRefresh: Boolean,
        val result: CompletableDeferred<OrbinResult<Thread>> = CompletableDeferred(),
        var cancelled: Boolean = false,
    )

    val requests = mutableListOf<Request>()

    override fun observeThread(
        key: ThreadKey,
        forceRefresh: Boolean,
    ): Flow<OrbinResult<Thread>> =
        flow {
            val request = Request(forceRefresh).also(requests::add)
            try {
                emit(request.result.await())
            } finally {
                request.cancelled = !currentCoroutineContext().isActive
            }
        }

    override suspend fun refreshThread(
        provider: ProviderId,
        board: BoardId,
        thread: ThreadId,
        forceRefresh: Boolean,
    ): OrbinResult<Thread> = error("The reader observes threads")
}
