package com.orbin.feature.gallery

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.result.DataError
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
import kotlinx.coroutines.flow.flow
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
            val pending = mutableListOf<Job>()
            val owners = mutableListOf<Any>()
            every { video.prefetch(any(), any(), capture(owners)) } answers {
                Job().also(pending::add)
            }
            val viewModel = createViewModel(FakeSettingsRepository(), video = video)
            val items =
                listOf(
                    attachment("image", MediaType.IMAGE),
                    attachment("next", MediaType.VIDEO),
                    attachment("covered", MediaType.VIDEO).copy(isSpoiler = true),
                )
            viewModel.prefetchAround(0, items)
            verify(exactly = 1) { video.prefetch("https://example.org/next", any(), any()) }
            viewModel.prefetchAround(1, items)
            assertThat(pending.single().isCancelled).isTrue()
            verify(exactly = 1) { video.prefetch(any(), any(), any()) }

            // Returning to the same page uses this gallery's existing owner, not a new token or
            // the legacy global owner. Its next cancelled request remains independently owned.
            viewModel.prefetchAround(0, items)
            assertThat(owners).hasSize(2)
            assertThat(owners[1]).isSameInstanceAs(owners[0])
            verify(exactly = 2) { video.prefetch("https://example.org/next", any(), match { it === owners[0] }) }
            viewModel.prefetchAround(1, items)
            assertThat(pending.map { it.isCancelled }).containsExactly(true, true)
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
                assertThat(media.map { it.id }).containsExactly("jpg", "webm", "gif", "mp4", "ogg").inOrder()
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
    fun `an explicit missing attachment shows unavailable instead of a different first file`() =
        runTest {
            val viewModel = createViewModel(FakeSettingsRepository(), attachmentId = "deleted")
            viewModel.content.test {
                var content = awaitItem()
                while (content is GalleryContentState.Loading) content = awaitItem()
                assertThat(content).isEqualTo(GalleryContentState.SelectedUnavailable)
            }
        }

    @Test
    fun `thread videos contain OP and reply videos but exclude images gifs and audio`() =
        runTest {
            val viewModel = createViewModel(FakeSettingsRepository(), videosOnly = true)

            viewModel.media.test {
                var media = awaitItem()
                while (media.isEmpty()) media = awaitItem()
                assertThat(media.map { it.id }).containsExactly("webm", "mp4").inOrder()
                assertThat(viewModel.threadMediaIndexAt(0)).isEqualTo(1)
                assertThat(viewModel.threadMediaIndexAt(1)).isEqualTo(3)
                assertThat(viewModel.threadMediaIndexAt(2)).isNull()
            }
        }

    @Test
    fun `video attachment identity wins over its former index in the complete thread`() =
        runTest {
            val viewModel = createViewModel(FakeSettingsRepository(), videosOnly = true, attachmentId = "mp4")

            viewModel.media.test {
                var media = awaitItem()
                while (media.isEmpty()) media = awaitItem()
                assertThat(viewModel.initialPageIn(media)).isEqualTo(1)
                assertThat(viewModel.threadMediaIndexAt(1)).isEqualTo(3)
            }
        }

    @Test
    fun `a loaded thread without videos is a ready empty viewer rather than loading or comments`() =
        runTest {
            val photosOnly =
                thread().copy(
                    originalPost =
                        thread().originalPost.copy(
                            attachments = persistentListOf(attachment("photo", MediaType.IMAGE)),
                        ),
                    replies = persistentListOf(),
                )
            val viewModel =
                createViewModel(
                    FakeSettingsRepository(),
                    videosOnly = true,
                    repository = FakeThreadRepository(photosOnly),
                )

            assertThat(viewModel.content.value).isEqualTo(GalleryContentState.Loading)
            viewModel.content.test {
                var state = awaitItem()
                while (state is GalleryContentState.Loading) state = awaitItem()
                val loaded = state as GalleryContentState.Ready
                assertThat(loaded.media).isEmpty()
                assertThat(loaded.threadMedia.map { it.id }).containsExactly("photo")
            }
        }

    @Test
    fun `thread loading errors remain retryable and force a new request`() =
        runTest {
            val repository = FakeThreadRepository(thread()).apply { result = OrbinResult.Failure(DataError.Offline()) }
            val viewModel = createViewModel(FakeSettingsRepository(), videosOnly = true, repository = repository)

            viewModel.content.test {
                var failed = awaitItem()
                while (failed is GalleryContentState.Loading) failed = awaitItem()
                assertThat((failed as GalleryContentState.Error).message).isEqualTo("No network connection")
                repository.result = OrbinResult.Success(thread())
                viewModel.retry()
                var loaded = awaitItem()
                while (loaded is GalleryContentState.Loading) loaded = awaitItem()
                assertThat(
                    (loaded as GalleryContentState.Ready).media.map { it.id },
                ).containsExactly("webm", "mp4").inOrder()
                assertThat(repository.refreshes).containsExactly(false, true).inOrder()
            }
        }

    @Test
    fun `video mode still excludes permanently filtered replies`() =
        runTest {
            val original = thread()
            val filtered =
                original.copy(
                    replies = persistentListOf(original.replies.first().copy(subject = "gore")),
                )
            val viewModel =
                createViewModel(
                    FakeSettingsRepository(),
                    videosOnly = true,
                    repository = FakeThreadRepository(filtered),
                )

            viewModel.media.test {
                var media = awaitItem()
                while (media.isEmpty()) media = awaitItem()
                assertThat(media.map { it.id }).containsExactly("webm")
            }
        }

    @Test
    fun `reused video ids keep separate pages and original reply positions in both gallery modes`() =
        runTest {
            val original = thread()
            val repeatedVideo = attachment("same-video-url", MediaType.VIDEO)
            val repeated =
                original.copy(
                    originalPost =
                        original.originalPost.copy(
                            attachments = persistentListOf(attachment("photo", MediaType.IMAGE), repeatedVideo),
                        ),
                    replies =
                        persistentListOf(
                            original.replies.first().copy(
                                attachments = persistentListOf(attachment("between", MediaType.IMAGE), repeatedVideo),
                            ),
                        ),
                )
            for (videosOnly in listOf(false, true)) {
                val viewModel =
                    createViewModel(
                        FakeSettingsRepository(),
                        videosOnly = videosOnly,
                        repository = FakeThreadRepository(repeated),
                    )
                viewModel.content.test {
                    var state = awaitItem()
                    while (state is GalleryContentState.Loading) state = awaitItem()
                    val loaded = state as GalleryContentState.Ready
                    val expectedIndices = if (videosOnly) listOf(1, 3) else listOf(0, 1, 2, 3)
                    assertThat(loaded.threadMediaIndices).containsExactlyElementsIn(expectedIndices).inOrder()
                    assertThat(
                        loaded.media.indices
                            .map(loaded::pageKey)
                            .distinct(),
                    ).hasSize(loaded.media.size)
                    assertThat(viewModel.threadMediaIndexAt(loaded.media.lastIndex)).isEqualTo(3)
                    if (videosOnly) {
                        assertThat(
                            loaded.media.map { it.id },
                        ).containsExactly(repeatedVideo.id, repeatedVideo.id)
                    }
                }
            }
        }

    private fun createViewModel(
        settingsRepository: SettingsRepository,
        preloader: ImagePreloader = mockk(relaxed = true),
        video: VideoPreloader = mockk(relaxed = true),
        videosOnly: Boolean = false,
        attachmentId: String? = null,
        repository: ThreadRepository = FakeThreadRepository(thread()),
    ) = GalleryViewModel(
        savedStateHandle =
            SavedStateHandle(
                mapOf(
                    "provider" to PROVIDER,
                    "board" to BOARD,
                    "thread" to THREAD,
                    "startIndex" to 0,
                    "videosOnly" to videosOnly,
                    "attachmentId" to attachmentId,
                ),
            ),
        observeThread = ObserveThreadUseCase(repository),
        downloadRepository = FakeDownloadRepository(),
        settingsRepository = settingsRepository,
        savedThreadRepository = FakeSavedThreadRepository(),
        imageClipboard = mockk(relaxed = true),
        imagePreloader = preloader,
        videoPreloader = video,
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
                        attachments =
                            persistentListOf(
                                attachment("gif", MediaType.ANIMATED_IMAGE),
                                attachment("mp4", MediaType.VIDEO),
                                attachment("ogg", MediaType.AUDIO),
                            ),
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
    var result: OrbinResult<Thread> = OrbinResult.Success(thread)
    val refreshes = mutableListOf<Boolean>()

    override fun observeThread(
        key: ThreadKey,
        forceRefresh: Boolean,
    ): Flow<OrbinResult<Thread>> =
        flow {
            refreshes += forceRefresh
            emit(result)
        }

    override suspend fun refreshThread(
        provider: ProviderId,
        board: BoardId,
        thread: ThreadId,
        forceRefresh: Boolean,
    ): OrbinResult<Thread> = result
}
