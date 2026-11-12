package com.orbin.feature.home

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeBoardPreferencesRepository
import com.orbin.core.testing.repository.FakeBoardRepository
import com.orbin.core.testing.repository.FakeImageBoardProvider
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.usecase.ObserveActiveProviderUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeBoardFollowingTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `first run subscriptions appear as followed and unfollow persists across relaunch`() =
        runTest {
            val provider = ProviderId("fourchan")
            val preferences = Preferences(subscribed = mapOf(provider to setOf(BoardId("g"))))
            val settings = FakeSettingsRepository()
            val model = createModel(preferences, settings)
            backgroundScope.launch { model.followedBoardIds.collect {} }
            runCurrent()

            assertThat(model.followedBoardIds.value).containsExactly("g")
            model.setFollowed("g", false, "fourchan")
            runCurrent()

            assertThat(model.followedBoardIds.value).isEmpty()
            assertThat(preferences.subscribed.value[provider]).isEmpty()
            assertThat(preferences.favorites.value[provider].orEmpty()).isEmpty()
            model.viewModelScope.cancel()

            val relaunched = createModel(preferences, settings)
            backgroundScope.launch { relaunched.followedBoardIds.collect {} }
            runCurrent()
            assertThat(relaunched.followedBoardIds.value).isEmpty()
            relaunched.viewModelScope.cancel()
        }

    @Test
    fun `a new board follow persists as a subscription without creating a favorite`() =
        runTest {
            val provider = ProviderId("fourchan")
            val preferences = Preferences()
            val settings = FakeSettingsRepository()
            val model = createModel(preferences, settings)
            backgroundScope.launch { model.followedBoardIds.collect {} }
            runCurrent()

            model.setFollowed("g", true, sourceId = "fourchan")
            runCurrent()

            assertThat(preferences.subscribed.value[provider]).containsExactly(BoardId("g"))
            assertThat(preferences.favorites.value).isEmpty()
            assertThat(preferences.writes).containsExactly("subscription:true")
            assertThat(model.followedBoardIds.value).containsExactly("g")
            model.viewModelScope.cancel()

            val relaunched = createModel(preferences, settings)
            backgroundScope.launch { relaunched.followedBoardIds.collect {} }
            runCurrent()
            assertThat(relaunched.followedBoardIds.value).containsExactly("g")
            assertThat(preferences.favorites.value).isEmpty()
            relaunched.viewModelScope.cancel()
        }

    @Test
    fun `overlapping follows count once and source qualified unfollow leaves other sites alone`() =
        runTest {
            val fourchan = ProviderId("fourchan")
            val bbwchan = ProviderId("bbwchan")
            val both = mapOf(fourchan to setOf(BoardId("g")), bbwchan to setOf(BoardId("g")))
            val preferences = Preferences(subscribed = both, favorites = both)
            val model = createModel(preferences, FakeSettingsRepository())
            backgroundScope.launch { model.followedBoardIds.collect {} }
            runCurrent()
            assertThat(model.followedBoardIds.value).containsExactly("g")

            model.selectSite("bbwchan")
            runCurrent()
            model.setFollowed("g", false, sourceId = "fourchan")
            runCurrent()

            assertThat(model.followedBoardIds.value).containsExactly("g")
            assertThat(preferences.subscribed.value[fourchan]).isEmpty()
            assertThat(preferences.favorites.value[fourchan]).containsExactly(BoardId("g"))
            assertThat(preferences.subscribed.value[bbwchan]).containsExactly(BoardId("g"))
            assertThat(preferences.favorites.value[bbwchan]).containsExactly(BoardId("g"))
            model.viewModelScope.cancel()
        }

    @Test
    fun `a queued follow cannot be overwritten by an earlier unsubscribe`() =
        runTest {
            val provider = ProviderId("fourchan")
            val existing = mapOf(provider to setOf(BoardId("g")))
            val preferences = Preferences(subscribed = existing, favorites = existing)
            val saving = CompletableDeferred<Unit>()
            preferences.beforeWrite = { store, followed ->
                if (store == "subscription" && !followed) saving.await()
            }
            val model = createModel(preferences, FakeSettingsRepository())
            backgroundScope.launch { model.followedBoardIds.collect {} }
            runCurrent()

            model.setFollowed("g", false)
            model.setFollowed("g", true)
            runCurrent()
            assertThat(preferences.writes).isEmpty()

            saving.complete(Unit)
            runCurrent()
            assertThat(preferences.writes)
                .containsExactly("subscription:false", "subscription:true")
                .inOrder()
            assertThat(preferences.subscribed.value[provider]).containsExactly(BoardId("g"))
            assertThat(preferences.favorites.value[provider]).containsExactly(BoardId("g"))
            assertThat(model.followedBoardIds.value).containsExactly("g")
            model.viewModelScope.cancel()
        }

    @Test
    fun `a favorite without a subscription is never marked followed`() =
        runTest {
            val provider = ProviderId("fourchan")
            val preferences = Preferences(favorites = mapOf(provider to setOf(BoardId("g"))))
            val model = createModel(preferences, FakeSettingsRepository())
            backgroundScope.launch { model.followedBoardIds.collect {} }
            runCurrent()

            assertThat(model.followedBoardIds.value).isEmpty()
            assertThat(model.followedBoards.value.contains("fourchan", "g")).isFalse()
            assertThat(preferences.subscribed.value).isEmpty()
            assertThat(preferences.favorites.value[provider]).containsExactly(BoardId("g"))
            assertThat(preferences.writes).isEmpty()
            model.viewModelScope.cancel()
        }

    @Test
    fun `switching sites never reuses a subscribed flag while the new site preferences are loading`() =
        runTest {
            val stored = Preferences(subscribed = mapOf(ProviderId("fourchan") to setOf(BoardId("g"))))
            val nextSite = MutableSharedFlow<Set<BoardId>>(replay = 1)
            val preferences =
                object : BoardPreferencesRepository by stored {
                    override fun observeSubscribedBoards(provider: ProviderId) =
                        if (provider == ProviderId("bbwchan")) nextSite else stored.observeSubscribedBoards(provider)
                }
            val model = createModel(preferences, FakeSettingsRepository())
            backgroundScope.launch { model.followedBoardIds.collect {} }
            runCurrent()
            assertThat(model.followedBoardIds.value).containsExactly("g")

            model.selectSite("bbwchan")
            runCurrent()
            assertThat(model.providerId.value).isEqualTo("bbwchan")
            assertThat(model.followedBoardIds.value).isEmpty()
            assertThat(model.followedBoards.value.contains("bbwchan", "g")).isFalse()

            nextSite.emit(emptySet())
            runCurrent()
            assertThat(model.followedBoardIds.value).isEmpty()
            model.selectSite("fourchan")
            runCurrent()
            assertThat(model.followedBoardIds.value).containsExactly("g")
            model.viewModelScope.cancel()
        }

    private fun createModel(
        preferences: BoardPreferencesRepository,
        settings: FakeSettingsRepository,
    ): HomeViewModel {
        val providers = listOf(FakeImageBoardProvider("fourchan"), FakeImageBoardProvider("bbwchan"))
        val registry =
            object : ProviderRegistry {
                override fun all(): List<ImageBoardProvider> = providers

                override fun get(id: ProviderId): ImageBoardProvider? = providers.firstOrNull { it.metadata.id == id }

                override fun default(): ImageBoardProvider = providers.first()
            }
        return HomeViewModel(
            registry,
            ObserveActiveProviderUseCase(registry, settings),
            FakeBoardRepository(),
            preferences,
            settings,
        )
    }

    private class Preferences(
        subscribed: Map<ProviderId, Set<BoardId>> = emptyMap(),
        favorites: Map<ProviderId, Set<BoardId>> = emptyMap(),
    ) : BoardPreferencesRepository by FakeBoardPreferencesRepository(subscribed = emptySet()) {
        val subscribed = MutableStateFlow(subscribed)
        val favorites = MutableStateFlow(favorites)
        val writes = mutableListOf<String>()
        var beforeWrite: suspend (String, Boolean) -> Unit = { _, _ -> }

        override fun observeSubscribedBoards(provider: ProviderId) = subscribed.map { it[provider].orEmpty() }

        override fun observeFavoriteBoards(provider: ProviderId) = favorites.map { it[provider].orEmpty() }

        override suspend fun setSubscribedBoard(
            provider: ProviderId,
            board: BoardId,
            subscribed: Boolean,
        ) {
            beforeWrite("subscription", subscribed)
            this.subscribed.update { current ->
                val boards = current[provider].orEmpty()
                current + (provider to if (subscribed) boards + board else boards - board)
            }
            writes += "subscription:$subscribed"
        }

        override suspend fun setFavoriteBoard(
            provider: ProviderId,
            board: BoardId,
            favorite: Boolean,
        ) {
            beforeWrite("favorite", favorite)
            favorites.update { current ->
                val boards = current[provider].orEmpty()
                current + (provider to if (favorite) boards + board else boards - board)
            }
            writes += "favorite:$favorite"
        }
    }
}
