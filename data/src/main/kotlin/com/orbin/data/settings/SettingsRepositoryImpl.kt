package com.orbin.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.network.NetworkConfig
import com.orbin.network.NetworkConfigProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SettingsRepository] and [BoardPreferencesRepository] persisted with DataStore Preferences, both
 * shared with iOS (`:storage`) over this same store. Also implements [NetworkConfigProvider], which
 * is fixed: HTTPS only, DNS over HTTPS through Cloudflare, the default user agent and the default
 * timeouts. None of those had a row anyone could reach.
 */
@Singleton
class SettingsRepositoryImpl
    @Inject
    constructor(
        dataStore: DataStore<Preferences>,
    ) : SettingsRepository by SettingsStore(dataStore),
        BoardPreferencesRepository by BoardPreferencesStore(dataStore),
        NetworkConfigProvider {
        override fun current(): NetworkConfig = FIXED_NETWORK_CONFIG
    }

private val FIXED_NETWORK_CONFIG = NetworkConfig()
