package com.orbin.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.AppSettings
import com.orbin.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Exposes the persisted [AppSettings] so the activity can theme the whole app reactively. */
@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings.Default)
    }
