package com.orbin.feature.gallery

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.result.DataError
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeDownloadRepository
import com.orbin.core.testing.repository.FakeSavedThreadRepository
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.repository.ThreadRepository
import com.orbin.domain.usecase.ObserveThreadUseCase
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** A known full-size clip can play while its thread is still being fetched. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GallerySeededPlaybackTest {
    @get:Rule
    val dispatcher = MainDispatcherRule()

    private val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(91L))
    private val selected =
        attachment("selected").copy(
            originalFileName = "selected \"clip\".webm",
            sourceUrl = "https://media.example/original/selected.webm?download=1&name=clip",
            thumbnailUrl = "https://media.example/preview/selected.jpg?size=small",
            width = 1920,
            height = 1080,
            thumbnailWidth = 250,
            thumbnailHeight = 141,
            sizeBytes = 5_000_000,
            durationMs = 18_500L,
        )

    @Test
    fun selectedFullSourceIsReadyBeforeTheThreadProducesAResponse() =
        runTest {
            val repository = PendingThreadRepository()
            val viewModel = viewModel(repository)
            val immediate = viewModel.content.value as GalleryContentState.Ready
            assertThat(immediate.media).containsExactly(selected)
            assertThat(immediate.media.single().sourceUrl).isNotEqualTo(selected.thumbnailUrl)
            assertThat(viewModel.threadMediaIndexAt(0)).isNull()
            assertThat(immediate.threadMediaIndexAt(0)).isNull()

            viewModel.content.test {
                assertThat(awaitItem()).isEqualTo(immediate)
                runCurrent()
                assertThat(repository.requests).containsExactly(key to false)
                expectNoEvents()
            }
        }

    @Test
    fun aThreadFailureAndRetryKeepTheKnownClipPlayable() =
        runTest {
            val repository = PendingThreadRepository()
            val viewModel = viewModel(repository)
            viewModel.content.test {
                val ready = awaitItem() as GalleryContentState.Ready
                runCurrent()
                repository.results.emit(OrbinResult.Failure(DataError.Offline()))
                runCurrent()
                assertThat(viewModel.content.value).isEqualTo(ready)
                expectNoEvents()

                viewModel.retry()
                runCurrent()
                repository.retryStarted.await()
                assertThat(viewModel.content.value).isEqualTo(ready)
                assertThat(repository.requests).containsExactly(key to false, key to true).inOrder()
                expectNoEvents()
            }
        }

    @Test
    fun aThrownThreadRequestAlsoKeepsTheKnownClipPlayable() =
        runTest {
            val repository = PendingThreadRepository(failure = IllegalStateException("request failed"))
            val viewModel = viewModel(repository)
            viewModel.content.test {
                val ready = awaitItem() as GalleryContentState.Ready
                runCurrent()
                assertThat(viewModel.content.value).isEqualTo(ready)
                assertThat(ready.media.single()).isEqualTo(selected)
                expectNoEvents()
            }
        }

    @Test
    fun threadExpansionAndLaterPrependsRetainTheSelectedPageKeyAndFullSource() =
        runTest {
            val repository = PendingThreadRepository()
            val viewModel = viewModel(repository)
            val repeated = attachment("repeated")
            viewModel.content.test {
                val seed = awaitItem() as GalleryContentState.Ready
                val selectedPageKey = seed.pageKey(0)
                runCurrent()

                repository.results.emit(OrbinResult.Success(thread(listOf(repeated, selected, repeated))))
                val expanded = awaitItem() as GalleryContentState.Ready
                assertThat(expanded.media).containsExactly(repeated, selected, repeated).inOrder()
                assertThat(viewModel.initialPageIn(expanded.media)).isEqualTo(1)
                assertThat(expanded.pageKey(1)).isEqualTo(selectedPageKey)
                assertThat(
                    expanded.media.indices
                        .map(expanded::pageKey)
                        .distinct(),
                ).hasSize(3)
                assertThat(viewModel.threadMediaIndexAt(1)).isEqualTo(1)

                repository.results.emit(
                    OrbinResult.Success(thread(listOf(attachment("new-before"), repeated, selected, repeated))),
                )
                val prepended = awaitItem() as GalleryContentState.Ready
                assertThat(viewModel.initialPageIn(prepended.media)).isEqualTo(2)
                assertThat(prepended.pageKey(2)).isEqualTo(selectedPageKey)
                assertThat(prepended.media[2].sourceUrl).isEqualTo(selected.sourceUrl)
                assertThat(
                    prepended.media.indices
                        .map(prepended::pageKey)
                        .distinct(),
                ).hasSize(4)
                assertThat(viewModel.threadMediaIndexAt(2)).isEqualTo(2)
            }
        }

    @Test
    fun aSuccessfulPartialThreadKeepsTheKnownSourceWithoutInventingItsThreadIndex() =
        runTest {
            val repository = PendingThreadRepository()
            val viewModel = viewModel(repository)
            val differentSource = selected.copy(sourceUrl = "https://media.example/original/different.webm")
            viewModel.content.test {
                val seed = awaitItem() as GalleryContentState.Ready
                runCurrent()
                repository.results.emit(OrbinResult.Success(thread(listOf(differentSource))))

                val partial = awaitItem() as GalleryContentState.Ready
                assertThat(partial.media).containsExactly(selected, differentSource).inOrder()
                assertThat(partial.threadMedia).containsExactly(differentSource)
                assertThat(partial.pageKey(0)).isEqualTo(seed.pageKey(0))
                assertThat(viewModel.initialPageIn(partial.media)).isEqualTo(0)
                assertThat(viewModel.threadMediaIndexAt(0)).isNull()
                assertThat(viewModel.threadMediaIndexAt(1)).isEqualTo(0)
            }
        }

    @Test
    fun anAuthoritativePermanentFilterRemovesTheSeedForThreadPostOrAttachment() =
        runTest {
            val base = thread(emptyList())
            val filteredThread = base.copy(originalPost = base.originalPost.copy(subject = "gore"))
            val selectedReply =
                Post(
                    id = PostId(92L),
                    board = BoardId("g"),
                    threadId = ThreadId(91L),
                    isOriginalPost = false,
                    attachments = persistentListOf(selected),
                )
            val filteredPost = base.copy(replies = persistentListOf(selectedReply.copy(subject = "gore")))
            val filteredAttachment =
                base.copy(
                    replies =
                        persistentListOf(
                            selectedReply.copy(
                                attachments = persistentListOf(selected.copy(originalFileName = "gore.webm")),
                            ),
                        ),
                )
            listOf(filteredThread, filteredPost, filteredAttachment).forEach { authoritative ->
                val repository = PendingThreadRepository()
                val viewModel = viewModel(repository)
                viewModel.content.test {
                    assertThat(awaitItem()).isInstanceOf(GalleryContentState.Ready::class.java)
                    runCurrent()
                    repository.results.emit(OrbinResult.Success(authoritative))
                    assertThat(awaitItem()).isEqualTo(GalleryContentState.SelectedUnavailable)

                    repository.results.emit(OrbinResult.Failure(DataError.Offline()))
                    assertThat(awaitItem()).isInstanceOf(GalleryContentState.Error::class.java)
                    assertThat(viewModel.threadMediaIndexAt(0)).isNull()
                }
            }
        }

    @Test
    fun seedRoundTripPreservesTheAttachmentAndRejectsEverySourceIdentityMismatch() {
        val encoded = encode(selected)
        assertThat(decodeGalleryMediaSeed(encoded, key, selected.id)).isEqualTo(selected)
        listOf(
            ThreadKey(ProviderId("bbwchan"), key.board, key.thread),
            ThreadKey(key.provider, BoardId("p"), key.thread),
            ThreadKey(key.provider, key.board, ThreadId(92L)),
        ).forEach { wrongKey ->
            assertThat(decodeGalleryMediaSeed(encoded, wrongKey, selected.id)).isNull()
        }
        assertThat(decodeGalleryMediaSeed(encoded, key, "other-file")).isNull()
        assertThat(decodeGalleryMediaSeed("{broken", key, selected.id)).isNull()
        assertThat(decodeGalleryMediaSeed(null, key, selected.id)).isNull()
    }

    @Test
    fun anUntrustedSeedCannotBypassThreadLoading() =
        runTest {
            val foreignSeed =
                checkNotNull(encodeGalleryMediaSeed("bbwchan", "g", 91L, selected))
            val viewModel = viewModel(PendingThreadRepository(), encodedSeed = foreignSeed)
            assertThat(viewModel.content.value).isEqualTo(GalleryContentState.Loading)
            assertThat(encodeGalleryMediaSeed("fourchan", "g", 91L, selected.copy(sourceUrl = ""))).isNull()
            assertThat(
                encodeGalleryMediaSeed("fourchan", "g", 91L, selected.copy(sourceUrl = "file:///tmp/clip.webm")),
            ).isNull()
        }

    private fun encode(attachment: MediaAttachment) =
        checkNotNull(encodeGalleryMediaSeed("fourchan", "g", 91L, attachment))

    private fun viewModel(
        repository: ThreadRepository,
        encodedSeed: String = encode(selected),
    ) = GalleryViewModel(
        savedStateHandle =
            SavedStateHandle(
                mapOf(
                    "provider" to "fourchan",
                    "board" to "g",
                    "thread" to 91L,
                    "attachmentId" to selected.id,
                    "thumbnailUrl" to selected.thumbnailUrl,
                    "encodedMediaSeed" to encodedSeed,
                ),
            ),
        observeThread = ObserveThreadUseCase(repository),
        downloadRepository = FakeDownloadRepository(),
        settingsRepository = FakeSettingsRepository(),
        savedThreadRepository = FakeSavedThreadRepository(),
        imageClipboard = mockk(relaxed = true),
        imagePreloader = mockk(relaxed = true),
        videoPreloader = mockk(relaxed = true),
    )

    private fun thread(attachments: List<MediaAttachment>) =
        Thread(
            key = key,
            originalPost =
                Post(
                    id = PostId(91L),
                    board = BoardId("g"),
                    threadId = ThreadId(91L),
                    isOriginalPost = true,
                    attachments = attachments.toImmutableList(),
                ),
            replies = persistentListOf(),
            stats = ThreadStats(),
        )

    private fun attachment(id: String) =
        MediaAttachment(
            id = id,
            originalFileName = "$id.webm",
            extension = "webm",
            type = MediaType.VIDEO,
            sourceUrl = "https://media.example/original/$id.webm",
            thumbnailUrl = "https://media.example/preview/$id.jpg",
        )
}

private class PendingThreadRepository(
    private val failure: Throwable? = null,
) : ThreadRepository {
    val results = MutableSharedFlow<OrbinResult<Thread>>()
    val requests = mutableListOf<Pair<ThreadKey, Boolean>>()
    val retryStarted = CompletableDeferred<Unit>()

    override fun observeThread(
        key: ThreadKey,
        forceRefresh: Boolean,
    ): Flow<OrbinResult<Thread>> =
        flow {
            requests += key to forceRefresh
            if (forceRefresh) retryStarted.complete(Unit)
            failure?.let { throw it }
            emitAll(results)
        }

    override suspend fun refreshThread(
        provider: ProviderId,
        board: BoardId,
        thread: ThreadId,
        forceRefresh: Boolean,
    ): OrbinResult<Thread> = error("unused")
}
