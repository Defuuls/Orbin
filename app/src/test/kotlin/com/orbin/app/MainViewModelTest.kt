package com.orbin.app

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.network.NetworkMonitor
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppUpdateState
import com.orbin.domain.repository.AppUpdater
import com.orbin.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @Test
    fun `readiness is never emitted with the default unlocked policy`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val persisted = MutableSharedFlow<AppSettings>(replay = 1)
            val repository = mockk<SettingsRepository> { every { settings } returns persisted }
            val network = mockk<NetworkMonitor> { every { isOnline } returns MutableStateFlow(true) }
            val updater = mockk<AppUpdater> { every { state } returns MutableStateFlow(AppUpdateState.Idle) }
            val viewModel = MainViewModel(repository, network, updater)
            try {
                val seen = mutableListOf<MainUiState>()
                backgroundScope.launch(
                    UnconfinedTestDispatcher(testScheduler),
                ) { viewModel.uiState.collect { seen += it } }
                assertThat(viewModel.uiState.value.ready).isFalse()

                persisted.emit(AppSettings(biometricLockEnabled = true, onboardingCompleted = true))

                assertThat(viewModel.uiState.value.ready).isTrue()
                assertThat(seen.filter { it.ready }.all { it.settings.biometricLockEnabled }).isTrue()
            } finally {
                viewModel.viewModelScope.cancel()
                Dispatchers.resetMain()
            }
        }
}
