package com.orbin.feature.gallery

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.AppSettings
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
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
import com.orbin.core.testing.repository.FakeBoardRepository
import com.orbin.core.testing.repository.FakeProviderRegistry
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderMetadata
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private const val PROVIDER = "fourchan"

/**
 * The wall's whole promise is that it holds *everything* without the reader picking a board, so
 * these cover the two ways that promise breaks: files going missing, and one bad board taking the
 * rest of the sweep down with it.
 */
class AllMediaViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tech = BoardId("g")
    private val anime = BoardId("a")

    @Test
    fun `sweeps every board into one list, in board order`() =
        runTest {
            val viewModel =
                createViewModel(
                    boards = listOf(Board(tech, "Technology"), Board(anime, "Anime")),
                    catalogs =
                        mapOf(
                            // Deliberately the alphabetically later board first: the wall must come
                            // back sorted by board, not in whatever order the map happens to hold.
                            tech to listOf(catalogThread(tech, 1L, image("g-1"))),
                            anime to listOf(catalogThread(anime, 2L, image("a-1"), image("a-2"))),
                        ),
                )

            val state = viewModel.awaitCompletedSweep()

            assertThat(state.items.map { it.attachment.id }).containsExactly("a-1", "a-2", "g-1").inOrder()
            assertThat(state.boardsScanned).isEqualTo(2)
            assertThat(state.boardsTotal).isEqualTo(2)
            assertThat(state.failedBoards).isEqualTo(0)
        }

    @Test
    fun `one board failing costs that board only`() =
        runTest {
            val viewModel =
                createViewModel(
                    boards = listOf(Board(tech, "Technology"), Board(anime, "Anime")),
                    catalogs = mapOf(tech to listOf(catalogThread(tech, 1L, image("g-1")))),
                    failing = setOf(anime),
                )

            val state = viewModel.awaitCompletedSweep()

            assertThat(state.items.map { it.attachment.id }).containsExactly("g-1")
            assertThat(state.failedBoards).isEqualTo(1)
            // Still counted as scanned: the sweep visited it, it just came back empty.
            assertThat(state.boardsScanned).isEqualTo(2)
        }

    @Test
    fun `the same file reposted on another board appears once`() =
        runTest {
            val viewModel =
                createViewModel(
                    boards = listOf(Board(tech, "Technology"), Board(anime, "Anime")),
                    catalogs =
                        mapOf(
                            tech to listOf(catalogThread(tech, 1L, image("dupe"))),
                            anime to listOf(catalogThread(anime, 2L, image("dupe"))),
                        ),
                )

            val state = viewModel.awaitCompletedSweep()

            assertThat(state.items).hasSize(1)
        }

    @Test
    fun `teaser replies contribute their media too`() =
        runTest {
            val thread =
                catalogThread(tech, 1L, image("op")).copy(
                    previewReplies =
                        listOf(
                            Post(
                                id = PostId(2),
                                board = tech,
                                threadId = ThreadId(1),
                                isOriginalPost = false,
                                attachments = listOf(image("reply")).toPersistentList(),
                            ),
                        ).toPersistentList(),
                )
            val viewModel =
                createViewModel(
                    boards = listOf(Board(tech, "Technology")),
                    catalogs = mapOf(tech to listOf(thread)),
                )

            val state = viewModel.awaitCompletedSweep()

            assertThat(state.items.map { it.attachment.id }).containsExactly("op", "reply").inOrder()
        }

    @Test
    fun `the permanent filter takes a thread and its media off the wall`() =
        runTest {
            val clean = catalogThread(tech, 1L, image("keep"))
            val filthy =
                catalogThread(tech, 2L, image("drop")).let { thread ->
                    thread.copy(originalPost = thread.originalPost.copy(subject = "gore thread"))
                }
            val viewModel =
                createViewModel(
                    boards = listOf(Board(tech, "Technology")),
                    catalogs = mapOf(tech to listOf(clean, filthy)),
                )

            val state = viewModel.awaitCompletedSweep()

            assertThat(state.items.map { it.attachment.id }).containsExactly("keep")
        }

    @Test
    fun `videos-only leaves the videos alone on the wall`() =
        runTest {
            val viewModel =
                createViewModel(
                    boards = listOf(Board(tech, "Technology")),
                    catalogs =
                        mapOf(
                            tech to
                                listOf(
                                    catalogThread(
                                        tech,
                                        1L,
                                        image("jpg"),
                                        attachment("webm", MediaType.VIDEO),
                                    ),
                                ),
                        ),
                    settings = AppSettings.Default.copy(mediaFilter = MediaFilter.VIDEOS),
                )

            val state = viewModel.awaitCompletedSweep()

            assertThat(state.items.map { it.attachment.id }).containsExactly("webm")
        }

    @Test
    fun `a hidden board is never swept at all`() =
        runTest {
            val viewModel =
                createViewModel(
                    boards = listOf(Board(tech, "Technology"), Board(anime, "Anime", isNsfw = true)),
                    catalogs =
                        mapOf(
                            tech to listOf(catalogThread(tech, 1L, image("g-1"))),
                            anime to listOf(catalogThread(anime, 2L, image("a-1"))),
                        ),
                    settings = AppSettings.Default.copy(hideNsfwBoards = true),
                )

            val state = viewModel.awaitCompletedSweep()

            // Not merely absent from the wall: the board is off the sweep, so its total drops too.
            assertThat(state.boardsTotal).isEqualTo(1)
            assertThat(state.items.map { it.attachment.id }).containsExactly("g-1")
        }

    /**
     * Waits out the progressive fill. The wall appends board by board, so the early emissions are
     * partial by design and only the settled state is worth asserting on.
     */
    private suspend fun AllMediaViewModel.awaitCompletedSweep(): AllMediaUiState =
        uiState.first { !it.isScanning && it.boardsTotal > 0 }

    private fun createViewModel(
        boards: List<Board>,
        catalogs: Map<BoardId, List<CatalogThread>>,
        failing: Set<BoardId> = emptySet(),
        settings: AppSettings = AppSettings.Default,
    ): AllMediaViewModel {
        val registry = FakeProviderRegistry(catalogProvider(catalogs, failing))
        val settingsRepository = FakeSettingsRepository(settings)
        return AllMediaViewModel(
            providerRegistry = registry,
            observeActiveProvider = ObserveActiveProviderUseCase(registry, settingsRepository),
            boardRepository = FakeBoardRepository(boards = boards),
            settingsRepository = settingsRepository,
        )
    }

    private fun catalogProvider(
        catalogs: Map<BoardId, List<CatalogThread>>,
        failing: Set<BoardId>,
    ) = object : ImageBoardProvider {
        override val metadata = ProviderMetadata(ProviderId(PROVIDER), "Test", "https://example.org")
        override val capabilities = ProviderCapabilities()

        override suspend fun getBoards(): List<Board> = emptyList()

        override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> {
            if (request.board in failing) throw ProviderException.NotFound("Resource not found")
            return catalogs[request.board].orEmpty()
        }

        override suspend fun getThread(
            board: BoardId,
            thread: ThreadId,
        ): Thread = throw ProviderException.NotFound("not used")
    }

    private fun catalogThread(
        board: BoardId,
        id: Long,
        vararg attachments: MediaAttachment,
    ) = CatalogThread(
        key = ThreadKey(ProviderId(PROVIDER), board, ThreadId(id)),
        originalPost =
            Post(
                id = PostId(id),
                board = board,
                threadId = ThreadId(id),
                isOriginalPost = true,
                attachments = attachments.toList().toPersistentList(),
            ),
        stats = ThreadStats(),
    )

    private fun image(id: String) = attachment(id, MediaType.IMAGE)

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
