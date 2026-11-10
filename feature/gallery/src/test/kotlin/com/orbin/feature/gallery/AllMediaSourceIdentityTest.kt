package com.orbin.feature.gallery

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.AppSettings
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
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
import com.orbin.core.testing.repository.FakeBoardRepository
import com.orbin.core.testing.repository.FakeDownloadRepository
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.api.ProviderRegistry
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test

class AllMediaSourceIdentityTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a board wall keeps its route source when the directory source differs or changes`() =
        runTest {
            val fourchan = CatalogProvider("fourchan")
            val bbwchan = CatalogProvider("bbwchan")
            val providers = listOf(fourchan, bbwchan)
            val registry =
                object : ProviderRegistry {
                    override fun all(): List<ImageBoardProvider> = providers

                    override fun get(id: ProviderId): ImageBoardProvider? =
                        providers.firstOrNull { it.metadata.id == id }

                    override fun default(): ImageBoardProvider = fourchan
                }
            val settings = FakeSettingsRepository(AppSettings.Default.copy(activeProviderId = "fourchan"))
            val viewModel =
                AllMediaViewModel(
                    savedStateHandle = SavedStateHandle(mapOf("provider" to "bbwchan", "board" to "g")),
                    providerRegistry = registry,
                    observeActiveProvider = ObserveActiveProviderUseCase(registry, settings),
                    boardRepository = FakeBoardRepository(boards = listOf(Board(BoardId("g"), "General"))),
                    settingsRepository = settings,
                    downloadRepository = FakeDownloadRepository(),
                )
            val state = withTimeout(10_000) { viewModel.uiState.first { it.boardsScanned == 1 && !it.isScanning } }
            assertThat(
                state.items
                    .single()
                    .key.provider,
            ).isEqualTo(ProviderId("bbwchan"))
            assertThat(fourchan.catalogCalls).isEqualTo(0)
            assertThat(bbwchan.catalogCalls).isEqualTo(1)

            settings.setActiveProviderId(ProviderId("bbwchan"))
            runCurrent()
            settings.setActiveProviderId(ProviderId("fourchan"))
            runCurrent()
            assertThat(
                viewModel.uiState.value.items
                    .single()
                    .key.provider,
            ).isEqualTo(ProviderId("bbwchan"))
            assertThat(fourchan.catalogCalls).isEqualTo(0)
            assertThat(bbwchan.catalogCalls).isEqualTo(1)
        }

    private class CatalogProvider(
        id: String,
    ) : ImageBoardProvider {
        override val metadata = ProviderMetadata(ProviderId(id), id, "https://example.org")
        override val capabilities = ProviderCapabilities()
        var catalogCalls = 0
            private set

        override suspend fun getBoards(): List<Board> = listOf(Board(BoardId("g"), "General"))

        override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> {
            catalogCalls++
            val key = ThreadKey(metadata.id, request.board, ThreadId(7))
            return listOf(
                CatalogThread(
                    key = key,
                    originalPost =
                        Post(
                            PostId(7),
                            request.board,
                            key.thread,
                            isOriginalPost = true,
                            attachments =
                                persistentListOf(
                                    MediaAttachment(
                                        id = metadata.id.value,
                                        originalFileName = "landscape.jpg",
                                        extension = "jpg",
                                        type = MediaType.IMAGE,
                                        sourceUrl = "https://example.org/${metadata.id.value}/landscape.jpg",
                                        thumbnailUrl = "https://example.org/${metadata.id.value}/thumb.jpg",
                                    ),
                                ),
                        ),
                    stats = ThreadStats(),
                ),
            )
        }

        override suspend fun getThread(
            board: BoardId,
            thread: ThreadId,
        ): Thread = error("Catalog-only fixture")
    }
}
