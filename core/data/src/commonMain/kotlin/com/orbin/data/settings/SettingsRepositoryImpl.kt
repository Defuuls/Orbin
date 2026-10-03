package com.orbin.data.settings

import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.SettingsRepository

/**
 * [SettingsRepository] and [BoardPreferencesRepository] persisted with DataStore Preferences, both
 * shared with iOS (`:storage`) over this same store. The stores come from the shared graph, so iOS
 * and Android read and write settings through the same instances' code.
 */
class SettingsRepositoryImpl(
    settings: SettingsStore,
    boards: BoardPreferencesStore,
) : SettingsRepository by settings,
    BoardPreferencesRepository by boards
