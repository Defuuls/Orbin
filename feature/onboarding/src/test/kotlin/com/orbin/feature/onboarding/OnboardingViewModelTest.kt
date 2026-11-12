package com.orbin.feature.onboarding

import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.result.DataError
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.ProviderId
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeBoardRepository
import com.orbin.core.testing.repository.FakeImageBoardProvider
import com.orbin.core.testing.repository.FakeProviderRegistry
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val provider = FakeImageBoardProvider(id = "fourchan")
    private val settings = FakeSettingsRepository()
    private val preferences = RecordingBoardPreferences()

    private fun viewModel(boards: BoardRepository = FakeBoardRepository(listOf(Board(BoardId("g"), "Technology")))) =
        OnboardingViewModel(FakeProviderRegistry(provider), boards, preferences, settings)

    @Test
    fun `the default site's boards load for the picker`() =
        runTest {
            val viewModel = viewModel()

            val loaded = viewModel.boards.first { it is OnboardingBoardsState.Success } as OnboardingBoardsState.Success

            assertThat(loaded.boards.map { it.id.value }).containsExactly("g")
        }

    @Test
    fun `a site that cannot be reached shows why and can be retried`() =
        runTest {
            val failing = FailingThenWorkingBoards()
            val viewModel = viewModel(failing)

            val error = viewModel.boards.first { it is OnboardingBoardsState.Error } as OnboardingBoardsState.Error
            assertThat(error.message).isEqualTo("No network connection")

            viewModel.loadBoards()
            assertThat(viewModel.boards.first { it !is OnboardingBoardsState.Loading })
                .isInstanceOf(OnboardingBoardsState.Success::class.java)
        }

    @Test
    fun `following a board writes it through and shows it as followed`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.setSubscribed("g", subscribed = true)
            advanceUntilIdle()

            assertThat(preferences.subscribed.value[provider.metadata.id]).containsExactly(BoardId("g"))
            assertThat(viewModel.subscribedBoardIds.first { it.isNotEmpty() }).containsExactly("g")
        }

    @Test
    fun `starting to browse finishes setup on the chosen site`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.setSubscribed("g", subscribed = true)
            advanceUntilIdle()

            viewModel.complete()
            advanceUntilIdle()

            assertThat(settings.current.onboardingCompleted).isTrue()
            assertThat(settings.current.activeProviderId).isEqualTo("fourchan")
            assertThat(viewModel.setupState.value.completed).isTrue()
        }

    @Test
    fun `setup cannot finish without a persisted followed board`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.complete()
            advanceUntilIdle()

            assertThat(settings.current.onboardingCompleted).isFalse()
            assertThat(viewModel.setupState.value.completed).isFalse()
            assertThat(viewModel.setupState.value.errorMessage).isNotEmpty()
        }

    @Test
    fun `a pending follow must reach storage before setup can finish`() =
        runTest {
            val persisted = CompletableDeferred<Unit>()
            preferences.beforeWrite = { persisted.await() }
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.setSubscribed("g", subscribed = true)
            viewModel.complete()
            runCurrent()

            assertThat(viewModel.setupState.value.isSaving).isTrue()
            assertThat(viewModel.totalFollowing.value).isEqualTo(0)
            assertThat(settings.current.onboardingCompleted).isFalse()

            persisted.complete(Unit)
            advanceUntilIdle()
            assertThat(viewModel.totalFollowing.value).isEqualTo(1)
            assertThat(settings.current.onboardingCompleted).isFalse()

            viewModel.complete()
            advanceUntilIdle()
            assertThat(settings.current.onboardingCompleted).isTrue()
        }

    @Test
    fun `a failed follow stays in setup and can be retried`() =
        runTest {
            preferences.beforeWrite = { throw IllegalStateException("disk unavailable") }
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.setSubscribed("g", subscribed = true)
            advanceUntilIdle()

            assertThat(viewModel.setupState.value.isSaving).isFalse()
            assertThat(viewModel.setupState.value.errorMessage).isNotEmpty()
            assertThat(viewModel.totalFollowing.value).isEqualTo(0)
            assertThat(settings.current.onboardingCompleted).isFalse()

            preferences.beforeWrite = {}
            viewModel.setSubscribed("g", subscribed = true)
            advanceUntilIdle()
            viewModel.complete()
            advanceUntilIdle()
            assertThat(viewModel.setupState.value.completed).isTrue()
        }

    @Test
    fun `completion acknowledgement waits for the settings write`() =
        runTest {
            val persisted = CompletableDeferred<Unit>()
            val delayedSettings =
                object : SettingsRepository by settings {
                    override suspend fun setOnboardingCompleted(completed: Boolean) {
                        persisted.await()
                        this@OnboardingViewModelTest.settings.setOnboardingCompleted(completed)
                    }
                }
            val viewModel =
                OnboardingViewModel(FakeProviderRegistry(provider), FakeBoardRepository(), preferences, delayedSettings)
            advanceUntilIdle()
            viewModel.setSubscribed("g", true)
            advanceUntilIdle()

            viewModel.complete()
            runCurrent()
            assertThat(viewModel.setupState.value.isSaving).isTrue()
            assertThat(viewModel.setupState.value.completed).isFalse()
            assertThat(settings.current.onboardingCompleted).isFalse()

            persisted.complete(Unit)
            advanceUntilIdle()
            assertThat(viewModel.setupState.value.completed).isTrue()
        }

    @Test
    fun `failed setup settings remain retryable and never acknowledge completion`() =
        runTest {
            var fail = true
            val failingSettings =
                object : SettingsRepository by settings {
                    override suspend fun setOnboardingCompleted(completed: Boolean) {
                        if (fail) error("disk unavailable")
                        this@OnboardingViewModelTest.settings.setOnboardingCompleted(completed)
                    }
                }
            val viewModel =
                OnboardingViewModel(FakeProviderRegistry(provider), FakeBoardRepository(), preferences, failingSettings)
            advanceUntilIdle()
            viewModel.setSubscribed("g", true)
            advanceUntilIdle()

            viewModel.complete()
            advanceUntilIdle()
            assertThat(viewModel.setupState.value.completed).isFalse()
            assertThat(viewModel.setupState.value.errorMessage).isNotEmpty()
            assertThat(settings.current.onboardingCompleted).isFalse()

            fail = false
            viewModel.complete()
            advanceUntilIdle()
            assertThat(viewModel.setupState.value.completed).isTrue()
        }

    @Test
    fun `switching sites during a pending follow keeps the write on its original site`() =
        runTest {
            val other = FakeImageBoardProvider(id = "bbwchan")
            val persisted = CompletableDeferred<Unit>()
            preferences.beforeWrite = { persisted.await() }
            val viewModel = OnboardingViewModel(registry(provider, other), FakeBoardRepository(), preferences, settings)
            advanceUntilIdle()

            viewModel.setSubscribed("g", true)
            viewModel.setSelectedProvider(other)
            runCurrent()
            persisted.complete(Unit)
            advanceUntilIdle()

            assertThat(preferences.subscribed.value[provider.metadata.id]).containsExactly(BoardId("g"))
            assertThat(preferences.subscribed.value[other.metadata.id]).isNull()
            assertThat(viewModel.subscribedBoardIds.value).isEmpty()
            assertThat(viewModel.totalFollowing.value).isEqualTo(1)

            viewModel.complete()
            advanceUntilIdle()
            assertThat(settings.current.onboardingCompleted).isTrue()
            assertThat(settings.current.activeProviderId).isEqualTo("fourchan")
        }

    @Test
    fun `same named boards on different sites are persisted and counted independently`() =
        runTest {
            val other = FakeImageBoardProvider(id = "bbwchan")
            val viewModel = OnboardingViewModel(registry(provider, other), FakeBoardRepository(), preferences, settings)
            advanceUntilIdle()
            viewModel.setSubscribed("g", true)
            advanceUntilIdle()
            viewModel.setSelectedProvider(other)
            advanceUntilIdle()
            viewModel.setSubscribed("g", true)
            advanceUntilIdle()

            assertThat(viewModel.totalFollowing.value).isEqualTo(2)
            assertThat(preferences.subscribed.value.keys).containsExactly(provider.metadata.id, other.metadata.id)
            viewModel.complete()
            advanceUntilIdle()
            assertThat(settings.current.activeProviderId).isEqualTo("bbwchan")
        }

    @Test
    fun `favorites do not select boards or finish setup without an explicit subscription`() =
        runTest {
            preferences.favorites.value = mapOf(provider.metadata.id to setOf(BoardId("g")))
            val viewModel = viewModel()
            advanceUntilIdle()

            assertThat(viewModel.subscribedBoardIds.value).isEmpty()
            assertThat(viewModel.totalFollowing.value).isEqualTo(0)
            viewModel.complete()
            advanceUntilIdle()
            assertThat(viewModel.setupState.value.completed).isFalse()
            assertThat(settings.current.onboardingCompleted).isFalse()
            assertThat(preferences.subscribed.value).isEmpty()

            viewModel.setSubscribed("g", true)
            advanceUntilIdle()
            assertThat(viewModel.totalFollowing.value).isEqualTo(1)

            viewModel.complete()
            advanceUntilIdle()
            assertThat(viewModel.setupState.value.completed).isTrue()
        }

    @Test
    fun `queued follow changes retain order before completion`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.setSubscribed("g", true)
            viewModel.setSubscribed("g", false)
            advanceUntilIdle()

            assertThat(viewModel.totalFollowing.value).isEqualTo(0)
            viewModel.complete()
            advanceUntilIdle()
            assertThat(settings.current.onboardingCompleted).isFalse()
        }

    @Test
    fun `unsubscribing preserves an independent favorite without allowing setup to complete`() =
        runTest {
            preferences.favorites.value = mapOf(provider.metadata.id to setOf(BoardId("g")))
            preferences.subscribed.value = mapOf(provider.metadata.id to setOf(BoardId("g")))
            val viewModel = viewModel()
            advanceUntilIdle()
            assertThat(viewModel.totalFollowing.value).isEqualTo(1)

            viewModel.setSubscribed("g", false)
            advanceUntilIdle()
            viewModel.complete()
            advanceUntilIdle()

            assertThat(preferences.favorites.value[provider.metadata.id]).containsExactly(BoardId("g"))
            assertThat(preferences.subscribed.value[provider.metadata.id]).isEmpty()
            assertThat(viewModel.totalFollowing.value).isEqualTo(0)
            assertThat(settings.current.onboardingCompleted).isFalse()
        }

    @Test
    fun `restored hidden adult follows cannot unlock setup and visible choices remain available`() =
        runTest {
            settings.setHideNsfwBoards(true)
            preferences.subscribed.value = mapOf(provider.metadata.id to setOf(BoardId("adult")))
            val viewModel =
                viewModel(
                    FakeBoardRepository(
                        listOf(
                            Board(BoardId("g"), "Technology"),
                            Board(BoardId("adult"), "Adult", isNsfw = true),
                        ),
                    ),
                )
            advanceUntilIdle()

            val loaded = viewModel.boards.value as OnboardingBoardsState.Success
            assertThat(loaded.boards.map { it.id.value }).containsExactly("g")
            assertThat(viewModel.totalFollowing.value).isEqualTo(0)
            viewModel.complete()
            advanceUntilIdle()
            assertThat(settings.current.onboardingCompleted).isFalse()

            viewModel.setSubscribed("g", true)
            advanceUntilIdle()
            assertThat(viewModel.totalFollowing.value).isEqualTo(1)
            viewModel.complete()
            advanceUntilIdle()
            assertThat(settings.current.onboardingCompleted).isTrue()
        }

    private fun registry(vararg providers: ImageBoardProvider): ProviderRegistry =
        object : ProviderRegistry {
            override fun all(): List<ImageBoardProvider> = providers.toList()

            override fun get(id: ProviderId): ImageBoardProvider? = providers.firstOrNull { it.metadata.id == id }

            override fun default(): ImageBoardProvider = providers.first()
        }

    private class FailingThenWorkingBoards : BoardRepository {
        private var calls = 0

        override fun observeBoards(provider: ProviderId): Flow<List<Board>> = flowOf(emptyList())

        override suspend fun refreshBoards(provider: ProviderId): OrbinResult<List<Board>> =
            if (calls++ == 0) {
                OrbinResult.Failure(DataError.Offline())
            } else {
                OrbinResult.Success(listOf(Board(BoardId("g"), "Technology")))
            }
    }

    /** Keeps what is followed, per site, so a test can see the picker's writes. */
    private class RecordingBoardPreferences : BoardPreferencesRepository {
        val subscribed = MutableStateFlow<Map<ProviderId, Set<BoardId>>>(emptyMap())
        val favorites = MutableStateFlow<Map<ProviderId, Set<BoardId>>>(emptyMap())
        var beforeWrite: suspend () -> Unit = {}

        override fun observeFavoriteBoards(provider: ProviderId): Flow<Set<BoardId>> =
            favorites.map { it[provider].orEmpty() }

        override fun observeSubscribedBoards(provider: ProviderId): Flow<Set<BoardId>> =
            subscribed.map { it[provider].orEmpty() }

        override suspend fun setFavoriteBoard(
            provider: ProviderId,
            board: BoardId,
            favorite: Boolean,
        ) {
            beforeWrite()
            favorites.update { all ->
                val current = all[provider].orEmpty()
                all + (provider to if (favorite) current + board else current - board)
            }
        }

        override suspend fun setSubscribedBoard(
            provider: ProviderId,
            board: BoardId,
            subscribed: Boolean,
        ) {
            beforeWrite()
            this.subscribed.update { all ->
                val current = all[provider].orEmpty()
                all + (provider to if (subscribed) current + board else current - board)
            }
        }

        override fun observeFeedThreadLimit(
            provider: ProviderId,
            board: BoardId,
        ): Flow<FeedThreadLimit?> = flowOf(null)

        override suspend fun setFeedThreadLimit(
            provider: ProviderId,
            board: BoardId,
            limit: FeedThreadLimit?,
        ) = Unit
    }
}
