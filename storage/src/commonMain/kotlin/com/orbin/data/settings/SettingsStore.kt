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
import com.orbin.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [SettingsRepository] over a Preferences [DataStore], under the keys Android has always used.
 * Android's `SettingsRepositoryImpl` delegates to it over its encrypted settings store; iOS keeps
 * its own DataStore file, the same one `BoardPreferencesStore` writes.
 */
@Suppress("TooManyFunctions")
class SettingsStore(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
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
