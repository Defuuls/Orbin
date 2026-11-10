package com.orbin.app

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.network.NetworkMonitor
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppUpdateState
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.domain.repository.AppUpdater
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.api.ProviderRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @Test
    fun `an enabled lock is independent of imported onboarding state`() {
        for (onboarding in listOf(false, true)) {
            val settings = AppSettings(biometricLockEnabled = true, onboardingCompleted = onboarding)
            assertThat(MainUiState(settings, ready = true).shouldLock).isTrue()
            assertThat(MainUiState(settings, ready = false).shouldLock).isFalse()
            assertThat(MainUiState(settings.copy(biometricLockEnabled = false), ready = true).shouldLock).isFalse()
        }
    }

    @Test
    fun `readiness is never emitted with the default unlocked policy`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val persisted = MutableSharedFlow<AppSettings>(replay = 1)
            val repository = mockk<SettingsRepository> { every { settings } returns persisted }
            val network = mockk<NetworkMonitor> { every { isOnline } returns MutableStateFlow(true) }
            val updater = mockk<AppUpdater> { every { state } returns MutableStateFlow(AppUpdateState.Idle) }
            val preferences =
                mockk<BoardPreferencesRepository> {
                    every { observeSubscribedBoards(any()) } returns flowOf(setOf(BoardId("g")))
                    every { observeFavoriteBoards(any()) } returns flowOf(emptySet())
                }
            val viewModel = MainViewModel(repository, network, updater, preferences, registry("fourchan"))
            try {
                val seen = mutableListOf<MainUiState>()
                backgroundScope.launch(
                    UnconfinedTestDispatcher(testScheduler),
                ) { viewModel.uiState.collect { seen += it } }
                assertThat(viewModel.uiState.value.ready).isFalse()

                persisted.emit(AppSettings(biometricLockEnabled = true, onboardingCompleted = true))
                runCurrent()

                assertThat(viewModel.uiState.value.ready).isTrue()
                assertThat(seen.filter { it.ready }.all { it.settings.biometricLockEnabled }).isTrue()
            } finally {
                viewModel.viewModelScope.cancel()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `a previously skipped empty installation must initialize follows before Home`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val fixture = StartupFixture()
            val viewModel = fixture.viewModel()
            try {
                runCurrent()
                assertThat(viewModel.uiState.value.ready).isTrue()
                assertThat(viewModel.uiState.value.settings.onboardingCompleted).isFalse()
                assertThat(fixture.persisted.value.onboardingCompleted).isFalse()

                fixture.follows.value = mapOf(ProviderId("fourchan") to setOf(BoardId("g")))
                runCurrent()
                assertThat(viewModel.uiState.value.settings.onboardingCompleted).isFalse()

                fixture.persisted.value = fixture.persisted.value.copy(onboardingCompleted = true)
                runCurrent()
                assertThat(viewModel.uiState.value.settings.onboardingCompleted).isTrue()
            } finally {
                viewModel.viewModelScope.cancel()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `failed empty-install repair still shows setup and successful completion clears the local gate`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val fixture = StartupFixture()
            coEvery { fixture.repository.setOnboardingCompleted(false) } throws IOException("temporarily unavailable")
            val viewModel = fixture.viewModel()
            try {
                runCurrent()

                assertThat(viewModel.uiState.value.ready).isTrue()
                assertThat(viewModel.uiState.value.settings.onboardingCompleted).isFalse()
                assertThat(fixture.persisted.value.onboardingCompleted).isTrue()

                // Following a board alone cannot dismiss the mandatory picker after the failed
                // reset, even though its stored completion flag still says true.
                fixture.follows.value = mapOf(ProviderId("fourchan") to setOf(BoardId("g")))
                runCurrent()
                assertThat(viewModel.uiState.value.settings.onboardingCompleted).isFalse()

                // The wizard successfully writes true, which may produce no new DataStore
                // emission, then explicitly acknowledges that persistence to MainViewModel.
                fixture.repository.setOnboardingCompleted(true)
                viewModel.onOnboardingCompleted()
                runCurrent()

                assertThat(viewModel.uiState.value.ready).isTrue()
                assertThat(viewModel.uiState.value.settings.onboardingCompleted).isTrue()
            } finally {
                viewModel.viewModelScope.cancel()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `failed legacy initialization write still opens an existing populated feed`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val fixture = StartupFixture()
            fixture.persisted.value = fixture.persisted.value.copy(onboardingCompleted = false)
            fixture.follows.value = mapOf(ProviderId("fourchan") to setOf(BoardId("g")))
            coEvery { fixture.repository.setOnboardingCompleted(true) } throws IOException("temporarily unavailable")
            val viewModel = fixture.viewModel()
            try {
                runCurrent()

                assertThat(viewModel.uiState.value.ready).isTrue()
                assertThat(viewModel.uiState.value.settings.onboardingCompleted).isTrue()
                assertThat(fixture.persisted.value.onboardingCompleted).isFalse()

                fixture.follows.value = emptyMap()
                runCurrent()
                assertThat(viewModel.uiState.value.settings.onboardingCompleted).isTrue()
            } finally {
                viewModel.viewModelScope.cancel()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `initial follow storage is read before an initialized launch becomes ready`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val fixture = StartupFixture()
            val delayedFollows = MutableSharedFlow<Set<BoardId>>(replay = 1)
            every { fixture.preferences.observeSubscribedBoards(ProviderId("fourchan")) } returns delayedFollows
            val viewModel = fixture.viewModel()
            try {
                runCurrent()
                assertThat(viewModel.uiState.value.ready).isFalse()

                delayedFollows.emit(setOf(BoardId("g")))
                runCurrent()

                assertThat(viewModel.uiState.value.ready).isTrue()
                assertThat(viewModel.uiState.value.settings.onboardingCompleted).isTrue()
            } finally {
                viewModel.viewModelScope.cancel()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `follows on a different site preserve the initialized combined Home feed`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val fixture = StartupFixture()
            fixture.follows.value = mapOf(ProviderId("bbwchan") to setOf(BoardId("sci")))
            val viewModel = fixture.viewModel()
            try {
                runCurrent()
                assertThat(viewModel.uiState.value.ready).isTrue()
                assertThat(viewModel.uiState.value.settings.onboardingCompleted).isTrue()
            } finally {
                viewModel.viewModelScope.cancel()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `favorite boards also count as initialized because Home includes them`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val fixture = StartupFixture()
            every { fixture.preferences.observeFavoriteBoards(ProviderId("bbwchan")) } returns
                flowOf(setOf(BoardId("sci")))
            val viewModel = fixture.viewModel()
            try {
                runCurrent()
                assertThat(viewModel.uiState.value.settings.onboardingCompleted).isTrue()
                assertThat(fixture.persisted.value.onboardingCompleted).isTrue()
            } finally {
                viewModel.viewModelScope.cancel()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `legacy readers with followed boards retain their initialized feed without the old setup flag`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val fixture = StartupFixture()
            fixture.persisted.value = fixture.persisted.value.copy(onboardingCompleted = false)
            fixture.follows.value = mapOf(ProviderId("fourchan") to setOf(BoardId("g")))
            val viewModel = fixture.viewModel()
            try {
                runCurrent()
                assertThat(viewModel.uiState.value.ready).isTrue()
                assertThat(viewModel.uiState.value.settings.onboardingCompleted).isTrue()
                assertThat(fixture.persisted.value.onboardingCompleted).isTrue()
            } finally {
                viewModel.viewModelScope.cancel()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `intentionally unfollowing the last board does not reopen setup in the same session`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val fixture = StartupFixture()
            fixture.follows.value = mapOf(ProviderId("fourchan") to setOf(BoardId("g")))
            val viewModel = fixture.viewModel()
            try {
                runCurrent()
                assertThat(viewModel.uiState.value.settings.onboardingCompleted).isTrue()

                fixture.follows.value = emptyMap()
                runCurrent()

                assertThat(viewModel.uiState.value.settings.onboardingCompleted).isTrue()
                assertThat(fixture.persisted.value.onboardingCompleted).isTrue()
            } finally {
                viewModel.viewModelScope.cancel()
                Dispatchers.resetMain()
            }
        }

    private class StartupFixture {
        val persisted = MutableStateFlow(AppSettings(onboardingCompleted = true))
        val follows = MutableStateFlow<Map<ProviderId, Set<BoardId>>>(emptyMap())
        val repository =
            mockk<SettingsRepository> {
                every { settings } returns persisted
                coEvery { setOnboardingCompleted(any()) } coAnswers {
                    persisted.value = persisted.value.copy(onboardingCompleted = firstArg())
                }
            }
        val preferences =
            mockk<BoardPreferencesRepository> {
                every { observeSubscribedBoards(any()) } answers {
                    // ProviderId is an inline value class, so MockK receives its JVM String.
                    val provider = ProviderId(firstArg<String>())
                    follows.map { it[provider].orEmpty() }
                }
                every { observeFavoriteBoards(any()) } returns flowOf(emptySet())
            }

        fun viewModel(): MainViewModel =
            MainViewModel(
                repository,
                mockk<NetworkMonitor> { every { isOnline } returns MutableStateFlow(true) },
                mockk<AppUpdater> { every { state } returns MutableStateFlow(AppUpdateState.Idle) },
                preferences,
                registry("fourchan", "bbwchan"),
            )
    }
}

private fun registry(vararg ids: String): ProviderRegistry =
    mockk {
        every { all() } returns
            ids.map { id ->
                mockk<ImageBoardProvider> {
                    every { metadata } returns ProviderMetadata(ProviderId(id), id, "https://example.org")
                }
            }
    }
