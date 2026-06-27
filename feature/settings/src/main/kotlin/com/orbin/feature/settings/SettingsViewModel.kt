package com.orbin.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Exposes settings and update actions for the settings screen. */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings> =
            repository.settings
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings.Default)

        fun setThemeMode(mode: AppThemeMode) = update { repository.setThemeMode(mode) }

        fun setDynamicColor(enabled: Boolean) = update { repository.setDynamicColor(enabled) }

        fun setAmoled(enabled: Boolean) = update { repository.setAmoled(enabled) }

        fun setAutoplay(enabled: Boolean) = update { repository.setAutoplayVideos(enabled) }

        fun setMute(enabled: Boolean) = update { repository.setMuteByDefault(enabled) }

        fun setPreload(enabled: Boolean) = update { repository.setPreloadImages(enabled) }

        fun setDoh(enabled: Boolean) = update { repository.setDohEnabled(enabled) }

        fun setHttpsOnly(enabled: Boolean) = update { repository.setHttpsOnly(enabled) }

        fun setBiometricLock(enabled: Boolean) = update { repository.setBiometricLockEnabled(enabled) }

        private fun update(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
