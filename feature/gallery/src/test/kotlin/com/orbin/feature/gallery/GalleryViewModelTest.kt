package com.orbin.feature.gallery

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppSettings
import com.orbin.core.model.BoardId
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
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeDownloadRepository
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.repository.ThreadRepository
import com.orbin.domain.usecase.ObserveThreadUseCase
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
    fun `videos-only pages through the videos alone`() =
        runTest {
            val settings = FakeSettingsRepository(AppSettings.Default.copy(mediaFilter = MediaFilter.VIDEOS))
            val viewModel = createViewModel(settings)

            viewModel.media.test {
                var media = awaitItem()
                while (media.none { it.id == "webm" }) media = awaitItem()
                assertThat(media.map { it.id }).containsExactly("webm")
            }
        }

    @Test
    fun `images-only counts animated images as images`() =
        runTest {
            val settings = FakeSettingsRepository(AppSettings.Default.copy(mediaFilter = MediaFilter.IMAGES))
            val viewModel = createViewModel(settings)

            viewModel.media.test {
                var media = awaitItem()
                while (media.isEmpty()) media = awaitItem()
                assertThat(media.map { it.id }).containsExactly("jpg", "gif").inOrder()
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

    private fun createViewModel(settingsRepository: FakeSettingsRepository) =
        GalleryViewModel(
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
