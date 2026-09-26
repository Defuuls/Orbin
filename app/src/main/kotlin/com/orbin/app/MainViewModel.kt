package com.orbin.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.network.NetworkMonitor
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppUpdateState
import com.orbin.domain.repository.AppUpdater
import com.orbin.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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
    ) : ViewModel() {
        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings.Default)

        /**
         * True once the first persisted settings snapshot has loaded. The activity waits for this
         * before composing the app so first-run onboarding gating reads the real flag (no flash of
         * onboarding for returning users).
         */
        val ready: StateFlow<Boolean> =
            settingsRepository.settings
                .map { true }
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
