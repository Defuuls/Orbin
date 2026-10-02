package com.orbin.data.settings

import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.SettingsRepository
import me.tatarka.inject.annotations.Inject
import com.orbin.graph.AppScope

/**
 * [SettingsRepository] and [BoardPreferencesRepository] persisted with DataStore Preferences, both
 * shared with iOS (`:storage`) over this same store. The stores come from the shared graph, so iOS
 * and Android read and write settings through the same instances' code.
 */
@AppScope
class SettingsRepositoryImpl
    @Inject
    constructor(
        settings: SettingsStore,
        boards: BoardPreferencesStore,
    ) : SettingsRepository by settings,
        BoardPreferencesRepository by boards
