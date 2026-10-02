package com.orbin.data.settings

import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SettingsRepository] and [BoardPreferencesRepository] persisted with DataStore Preferences, both
 * shared with iOS (`:storage`) over this same store. The stores come from the shared graph, so iOS
 * and Android read and write settings through the same instances' code.
 */
@Singleton
class SettingsRepositoryImpl
    @Inject
    constructor(
        settings: SettingsStore,
        boards: BoardPreferencesStore,
    ) : SettingsRepository by settings,
        BoardPreferencesRepository by boards
