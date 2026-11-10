package com.orbin.feature.gallery

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppSettings
import com.orbin.core.model.BoardId
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.PlaybackPreferences
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.PlaybackSettings
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
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.repository.ThreadRepository
import com.orbin.domain.usecase.ObserveThreadUseCase
import com.orbin.media.ImagePreloader
import com.orbin.media.video.VideoPreloader
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private const val PROVIDER = "fourchan"
private const val BOARD = "g"
private const val THREAD = 1L

/**
 * The gallery pages through the same media list the thread view builds its indices from, so the
 * two must filter identically — otherwise a tapped thumbnail opens the wrong page.
 */
class GalleryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val key = ThreadKey(ProviderId(PROVIDER), BoardId(BOARD), ThreadId(THREAD))

    @Test
    fun `only the next revealed clip is prefetched and obsolete network work is cancelled`() =
        runTest {
            val video = mockk<VideoPreloader>(relaxed = true)
            val pending = Job()
            every { video.prefetch(any(), any()) } returns pending
            val viewModel = createViewModel(FakeSettingsRepository(), video = video)
            val items =
                listOf(
                    attachment("image", MediaType.IMAGE),
                    attachment("next", MediaType.VIDEO),
                    attachment("covered", MediaType.VIDEO).copy(isSpoiler = true),
                )
            viewModel.prefetchAround(0, items)
            verify(exactly = 1) { video.prefetch("https://example.org/next", any()) }
            viewModel.prefetchAround(1, items)
            assertThat(pending.isCancelled).isTrue()
            verify(exactly = 1) { video.prefetch(any(), any()) }
        }

    @Test
    fun `gallery prefetches video posters and never decodes video sources through Coil`() =
        runTest {
            val preloader = mockk<ImagePreloader>(relaxed = true)
            val viewModel = createViewModel(FakeSettingsRepository(), preloader)
            val items =
                listOf(
                    attachment("before", MediaType.IMAGE),
                    attachment("video", MediaType.VIDEO),
                    attachment("after", MediaType.ANIMATED_IMAGE),
                )
            viewModel.prefetchAround(1, items)
            verify {
                preloader.prefetchBatch(
                    listOf(
                        "https://example.org/before/thumb",
                        "https://example.org/video/thumb",
                        "https://example.org/after/thumb",
                    ),
                    any(),
                    any(),
                )
            }
        }

    @Test
    fun `disabled preload excludes neighbours and covered sources are never prefetched`() =
        runTest {
            val preloader = mockk<ImagePreloader>(relaxed = true)
            val settings =
                FakeSettingsRepository(
                    AppSettings(
                        playbackPreferences = PlaybackPreferences(defaults = PlaybackSettings(preloadNextClip = false)),
                    ),
                )
            val viewModel = createViewModel(settings, preloader)
            val items = listOf(attachment("current", MediaType.IMAGE), attachment("next", MediaType.IMAGE))
            viewModel.prefetchAround(0, items)
            verify { preloader.prefetchBatch(listOf("https://example.org/current"), any(), any()) }
            viewModel.prefetchAround(0, listOf(items.first().copy(isSpoiler = true)))
            verify { preloader.prefetchBatch(emptyList(), any(), any()) }
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `playback waits for stored preferences and then follows this board override`() =
        runTest {
            val stored = MutableSharedFlow<AppSettings>(replay = 1)
            val repository =
                object : SettingsRepository by FakeSettingsRepository() {
                    override val settings: Flow<AppSettings> = stored
                }
            val viewModel = createViewModel(repository)
            assertThat(viewModel.playbackSettings().autoplay).isFalse()
            assertThat(viewModel.playbackSettings().preloadNextClip).isFalse()

            val boardPlayback = PlaybackSettings(autoplay = false, startMuted = false, loopVideos = false)
            stored.emit(
                AppSettings(
                    playbackPreferences =
                        PlaybackPreferences().withSettings(
                            PlaybackScope(PROVIDER, BOARD),
                            boardPlayback,
                        ),
                ),
            )
            runCurrent()
            assertThat(viewModel.playbackSettings()).isEqualTo(boardPlayback)
        }

    @Test
    fun `media carries every attachment by default`() =
        runTest {
            val viewModel = createViewModel(FakeSettingsRepository())

            viewModel.media.test {
                var media = awaitItem()
                while (media.isEmpty()) media = awaitItem()
                assertThat(media.map { it.id }).containsExactly("jpg", "webm", "gif").inOrder()
            }
        }

    @Test
    fun `an attachment id opens at that file, wherever it sits in the thread`() =
        runTest {
            val viewModel = createViewModel(FakeSettingsRepository(), attachmentId = "gif")

            viewModel.media.test {
                var media = awaitItem()
                while (media.isEmpty()) media = awaitItem()
                // "gif" is on a reply, not the OP — an index from a catalog could never have found it.
                assertThat(viewModel.initialPageIn(media)).isEqualTo(2)
            }
        }

    @Test
    fun `an attachment no longer in the thread falls back to the index`() =
        runTest {
            val viewModel = createViewModel(FakeSettingsRepository(), attachmentId = "deleted")

            viewModel.media.test {
                var media = awaitItem()
                while (media.isEmpty()) media = awaitItem()
                assertThat(viewModel.initialPageIn(media)).isEqualTo(0)
            }
        }

    private fun createViewModel(
        settingsRepository: SettingsRepository,
        preloader: ImagePreloader = mockk(relaxed = true),
        video: VideoPreloader = mockk(relaxed = true),
    ) = GalleryViewModel(
        savedStateHandle =
            SavedStateHandle(
                mapOf(
                    "provider" to PROVIDER,
                    "board" to BOARD,
                    "thread" to THREAD,
                    "startIndex" to 0,
                ),
            ),
        observeThread = ObserveThreadUseCase(FakeThreadRepository(thread())),
        downloadRepository = FakeDownloadRepository(),
        settingsRepository = settingsRepository,
        savedThreadRepository = FakeSavedThreadRepository(),
        imageClipboard = mockk(relaxed = true),
        imagePreloader = preloader,
        videoPreloader = video,
    )

    private fun createViewModel(
        settingsRepository: FakeSettingsRepository,
        attachmentId: String,
    ) = GalleryViewModel(
        savedStateHandle =
            SavedStateHandle(
                mapOf(
                    "provider" to PROVIDER,
                    "board" to BOARD,
                    "thread" to THREAD,
                    "startIndex" to 0,
                    "attachmentId" to attachmentId,
                ),
            ),
        observeThread = ObserveThreadUseCase(FakeThreadRepository(thread())),
        downloadRepository = FakeDownloadRepository(),
        settingsRepository = settingsRepository,
        savedThreadRepository = FakeSavedThreadRepository(),
        // Never exercised here — these tests are about which media the gallery pages through,
        // not about copying one to the clipboard.
        imageClipboard = mockk(relaxed = true),
        imagePreloader = mockk(relaxed = true),
        videoPreloader = mockk(relaxed = true),
    )

    private fun thread() =
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
                        attachments = persistentListOf(attachment("gif", MediaType.ANIMATED_IMAGE)),
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
