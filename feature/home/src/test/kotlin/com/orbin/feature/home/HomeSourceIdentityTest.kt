package com.orbin.feature.home

import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeBoardPreferencesRepository
import com.orbin.core.testing.repository.FakeImageBoardProvider
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class HomeSourceIdentityTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `switching sources clears old rows and delayed card actions keep their owning source`() =
        runTest {
            val registry = registry()
            val pendingBoards = CompletableDeferred<List<Board>>()
            val boards =
                object : BoardRepository {
                    override fun observeBoards(provider: ProviderId): Flow<List<Board>> = flowOf(emptyList())

                    override suspend fun refreshBoards(provider: ProviderId): OrbinResult<List<Board>> =
                        OrbinResult.Success(
                            if (provider.value ==
                                "bbwchan"
                            ) {
                                pendingBoards.await()
                            } else {
                                listOf(Board(BoardId("g"), "Technology"))
                            },
                        )
                }
            val favorites = mutableListOf<ProviderId>()
            val preferences =
                object : BoardPreferencesRepository by FakeBoardPreferencesRepository() {
                    override suspend fun setFavoriteBoard(
                        provider: ProviderId,
                        board: BoardId,
                        favorite: Boolean,
                    ) {
                        favorites += provider
                    }
                }
            val settings = FakeSettingsRepository()
            val model =
                HomeViewModel(registry, ObserveActiveProviderUseCase(registry, settings), boards, preferences, settings)
            val initial = model.uiState.first { it is HomeUiState.Success } as HomeUiState.Success
            assertThat(initial.providerId).isEqualTo("fourchan")

            model.selectSite("bbwchan")
            runCurrent()
            assertThat(model.providerId.value).isEqualTo("bbwchan")
            assertThat(model.uiState.value).isEqualTo(HomeUiState.Loading)
            model.setFollowed("g", true, sourceId = initial.providerId)
            runCurrent()
            assertThat(favorites).containsExactly(ProviderId("fourchan"))

            pendingBoards.complete(listOf(Board(BoardId("g"), "General Discussion")))
            val loaded = model.uiState.first { it is HomeUiState.Success } as HomeUiState.Success
            assertThat(loaded.providerId).isEqualTo("bbwchan")
            assertThat(loaded.boards.single().title).isEqualTo("General Discussion")
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a late initial source response cannot replace the newly selected directory`() =
        runTest {
            val registry = registry()
            val initialBoards = CompletableDeferred<List<Board>>()
            val boards =
                object : BoardRepository {
                    override fun observeBoards(provider: ProviderId): Flow<List<Board>> = flowOf(emptyList())

                    override suspend fun refreshBoards(provider: ProviderId): OrbinResult<List<Board>> =
                        OrbinResult.Success(
                            if (provider.value == "fourchan") {
                                initialBoards.await()
                            } else {
                                listOf(Board(BoardId("g"), "General Discussion"))
                            },
                        )
                }
            val settings = FakeSettingsRepository()
            val model =
                HomeViewModel(
                    registry,
                    ObserveActiveProviderUseCase(registry, settings),
                    boards,
                    FakeBoardPreferencesRepository(),
                    settings,
                )
            runCurrent()
            model.selectSite("bbwchan")
            val selected = model.uiState.first { it is HomeUiState.Success } as HomeUiState.Success
            assertThat(selected.providerId).isEqualTo("bbwchan")
            initialBoards.complete(listOf(Board(BoardId("g"), "Technology")))
            runCurrent()
            assertThat((model.uiState.value as HomeUiState.Success).providerId).isEqualTo("bbwchan")
        }

    private fun registry(): ProviderRegistry {
        val providers = listOf(FakeImageBoardProvider("fourchan"), FakeImageBoardProvider("bbwchan"))
        return object : ProviderRegistry {
            override fun all(): List<ImageBoardProvider> = providers

            override fun get(id: ProviderId): ImageBoardProvider? = providers.firstOrNull { it.metadata.id == id }

            override fun default(): ImageBoardProvider = providers.first()
        }
    }
}
