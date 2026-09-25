package com.orbin.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.ProviderId
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.network.NetworkConfig
import com.orbin.network.NetworkConfigProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SettingsRepository] persisted with DataStore Preferences. Also implements
 * [NetworkConfigProvider], which is fixed: HTTPS only, DNS over HTTPS through Cloudflare, the
 * default user agent and the default timeouts. None of those had a row anyone could reach.
 */
@Singleton
@Suppress("TooManyFunctions")
class SettingsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository,
        // Board favourites, follows and feed limits are shared with iOS, over this same store.
        BoardPreferencesRepository by BoardPreferencesStore(dataStore),
        NetworkConfigProvider {
        override val settings: Flow<AppSettings> = dataStore.data.map { it.toAppSettings() }

        override suspend fun setHideNsfwBoards(enabled: Boolean) {
            edit { it[Keys.hideNsfwBoards] = enabled }
        }

        override suspend fun setDeepMediaScan(enabled: Boolean) {
            edit { it[Keys.deepMediaScan] = enabled }
        }

        override suspend fun setThemeMode(mode: AppThemeMode) {
            edit { it[Keys.themeMode] = mode.name }
        }

        override suspend fun setAmoled(enabled: Boolean) {
            edit { it[Keys.amoled] = enabled }
        }

        override suspend fun setBiometricLockEnabled(enabled: Boolean) {
            edit { it[Keys.biometricLock] = enabled }
        }

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            edit { it[Keys.onboardingCompleted] = completed }
        }

        override suspend fun setActiveProviderId(id: ProviderId) {
            edit { it[Keys.activeProviderId] = id.value }
        }

        override fun current(): NetworkConfig = FIXED_NETWORK_CONFIG

        private suspend fun edit(block: (MutablePreferences) -> Unit) {
            dataStore.edit { block(it) }
        }

        @Suppress("ComplexMethod")
        private fun Preferences.toAppSettings(): AppSettings =
            AppSettings(
                hideNsfwBoards = this[Keys.hideNsfwBoards] ?: false,
                deepMediaScan = this[Keys.deepMediaScan] ?: false,
                themeMode = this[Keys.themeMode]?.let(AppThemeMode::valueOf) ?: AppThemeMode.SYSTEM,
                amoled = this[Keys.amoled] ?: false,
                biometricLockEnabled = this[Keys.biometricLock] ?: false,
                activeProviderId = this[Keys.activeProviderId] ?: "",
                onboardingCompleted = this[Keys.onboardingCompleted] ?: false,
            )

        private object Keys {
            val hideNsfwBoards = booleanPreferencesKey("hide_nsfw_boards")
            val deepMediaScan = booleanPreferencesKey("deep_media_scan")
            val themeMode = stringPreferencesKey("theme_mode")
            val amoled = booleanPreferencesKey("amoled")
            val biometricLock = booleanPreferencesKey("biometric_lock")
            val activeProviderId = stringPreferencesKey("active_provider_id")
            val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        }
    }

private val FIXED_NETWORK_CONFIG = NetworkConfig()
