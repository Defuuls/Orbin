package com.orbin.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.network.NetworkMonitor
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppUpdateState
import com.orbin.data.settings.FixedNetworkConfigProvider
import com.orbin.domain.repository.AppUpdater
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Exposes the persisted [AppSettings] so the activity can theme the whole app reactively. */
@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        settingsRepository: SettingsRepository,
        networkMonitor: NetworkMonitor,
        private val appUpdater: AppUpdater,
        boardPreferencesRepository: BoardPreferencesRepository,
        registry: ProviderRegistry,
        networkConfigProvider: FixedNetworkConfigProvider = FixedNetworkConfigProvider(),
    ) : ViewModel() {
        private val startupOnboardingOverride = MutableStateFlow<Boolean?>(null)

        init {
            viewModelScope.launch {
                settingsRepository.settings.collect { settings ->
                    networkConfigProvider.updateDoh(settings.dohProvider, settings.strictDohEnabled)
                }
            }
        }

        val uiState: StateFlow<MainUiState> =
            flow {
                val initialSettings = settingsRepository.settings.first()
                // Read local follows once per launch. Older versions let readers skip setup;
                // a reader who later unfollows everything in this session stays in the app.
                val providers = registry.all()
                val hasFeedBoards =
                    providers.isNotEmpty() &&
                        combine(
                            providers.map { provider ->
                                boardPreferencesRepository.observeSubscribedBoards(provider.metadata.id)
                            },
                        ) { it.any { boards -> boards.isNotEmpty() } }.first()
                if (initialSettings.onboardingCompleted != hasFeedBoards) {
                    try {
                        settingsRepository.setOnboardingCompleted(hasFeedBoards)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // A transient write failure must not leave startup blank. Keep the
                        // correct first-run policy locally until the wizard acknowledges success.
                        startupOnboardingOverride.value = hasFeedBoards
                    }
                }
                emitAll(
                    combine(settingsRepository.settings, startupOnboardingOverride) { settings, override ->
                        MainUiState(
                            settings = override?.let { settings.copy(onboardingCompleted = it) } ?: settings,
                            ready = true,
                        )
                    },
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

        /** Called by the wizard only after its followed boards and completion settings persist. */
        fun onOnboardingCompleted() {
            startupOnboardingOverride.value = null
        }

        /** Starts `true` so a fresh launch never flashes an offline banner before the first callback. */
        val isOnline: StateFlow<Boolean> =
            networkMonitor.isOnline
                .stateIn(viewModelScope, SharingStarted.Eagerly, true)

        /** The in-app update dialog's state, shared with Settings' manual check. */
        val updateState: StateFlow<AppUpdateState> = appUpdater.state

        private var launchCheckDone = false

        /** Looks for a new release once per process; [AppUpdater] throttles across launches. */
        fun checkForUpdateOnLaunch(currentVersionName: String) {
            if (launchCheckDone) return
            launchCheckDone = true
            viewModelScope.launch { appUpdater.checkOnLaunch(currentVersionName) }
        }

        fun update() {
            when (val state = appUpdater.state.value) {
                is AppUpdateState.Available -> appUpdater.update(state.release)
                is AppUpdateState.Failed -> appUpdater.update(state.release)
                else -> Unit
            }
        }

        fun dismissUpdate() = appUpdater.dismiss()

        fun cancelUpdate() = appUpdater.cancel()

        fun openInstallPermissionSettings() = appUpdater.openInstallPermissionSettings()

        fun installUpdate() = appUpdater.installDownloaded()
    }

/** Settings and readiness are one emission, so a loaded flag cannot race ahead of the lock policy. */
data class MainUiState(
    val settings: AppSettings = AppSettings.Default,
    val ready: Boolean = false,
) {
    // Onboarding can come from a backup; it must never disable the local authentication policy.
    val shouldLock: Boolean get() = ready && settings.biometricLockEnabled
}
