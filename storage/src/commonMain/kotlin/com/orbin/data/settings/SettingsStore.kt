package com.orbin.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.CatalogMode
import com.orbin.core.model.DEFAULT_WIDE_FEED_COLUMNS
import com.orbin.core.model.DohProvider
import com.orbin.core.model.FormFactor
import com.orbin.core.model.PlaybackPreferences
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.PlaybackSettings
import com.orbin.core.model.ProviderId
import com.orbin.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    override suspend fun setPlaybackSettings(
        scope: PlaybackScope,
        settings: PlaybackSettings?,
    ) {
        edit { preferences ->
            val updated = preferences.playbackPreferences().withSettings(scope, settings)
            preferences[Keys.playback] = playbackJson.encodeToString(updated)
        }
    }

    override suspend fun setHideNsfwBoards(enabled: Boolean) {
        edit { it[Keys.hideNsfwBoards] = enabled }
    }

    override suspend fun setCoverViolentMedia(enabled: Boolean) {
        edit { it[Keys.coverViolentMedia] = enabled }
    }

    override suspend fun setDeepMediaScan(enabled: Boolean) {
        edit { it[Keys.deepMediaScan] = enabled }
    }

    override suspend fun setOpenThreadComments(enabled: Boolean) {
        edit { it[Keys.openThreadComments] = enabled }
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

    override suspend fun setScreenPrivacyEnabled(enabled: Boolean) {
        edit { it[Keys.screenPrivacy] = enabled }
    }

    override suspend fun setDohProvider(provider: DohProvider) {
        edit { it[Keys.dohProvider] = provider.name }
    }

    override suspend fun setStrictDohEnabled(enabled: Boolean) {
        edit { it[Keys.strictDoh] = enabled }
    }

    override suspend fun setSaveSearchHistory(enabled: Boolean) {
        edit { it[Keys.saveSearchHistory] = enabled }
    }

    override suspend fun setSaveBrowsingHistory(enabled: Boolean) {
        edit { it[Keys.saveBrowsingHistory] = enabled }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        edit { it[Keys.onboardingCompleted] = completed }
    }

    override suspend fun setActiveProviderId(id: ProviderId) {
        edit { it[Keys.activeProviderId] = id.value }
    }

    override suspend fun setFeedColumns(
        formFactor: FormFactor,
        columns: Int,
    ) {
        val key =
            when (formFactor) {
                FormFactor.PHONE -> return
                FormFactor.FOLDABLE -> Keys.unfoldedFeedColumns
                FormFactor.TABLET -> Keys.tabletFeedColumns
            }
        edit { it[key] = columns.coerceIn(1, formFactor.maxFeedColumns) }
    }

    override suspend fun setCatalogMode(mode: CatalogMode) {
        edit { it[Keys.catalogMode] = mode.name }
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    @Suppress("ComplexMethod")
    private fun Preferences.toAppSettings(): AppSettings =
        AppSettings(
            playbackPreferences = playbackPreferences(),
            hideNsfwBoards = this[Keys.hideNsfwBoards] ?: false,
            coverViolentMedia = this[Keys.coverViolentMedia] ?: true,
            deepMediaScan = this[Keys.deepMediaScan] ?: false,
            openThreadComments = this[Keys.openThreadComments] ?: false,
            themeMode = AppThemeMode.entries.firstOrNull { it.name == this[Keys.themeMode] } ?: AppThemeMode.SYSTEM,
            amoled = this[Keys.amoled] ?: false,
            biometricLockEnabled = this[Keys.biometricLock] ?: false,
            screenPrivacyEnabled = this[Keys.screenPrivacy] ?: false,
            dohProvider =
                DohProvider.entries.firstOrNull { it.name == this[Keys.dohProvider] }
                    ?: DohProvider.CLOUDFLARE,
            strictDohEnabled = this[Keys.strictDoh] ?: false,
            saveSearchHistory = this[Keys.saveSearchHistory] ?: false,
            saveBrowsingHistory = this[Keys.saveBrowsingHistory] ?: true,
            activeProviderId = this[Keys.activeProviderId] ?: "",
            onboardingCompleted = this[Keys.onboardingCompleted] ?: false,
            unfoldedFeedColumns = this[Keys.unfoldedFeedColumns] ?: DEFAULT_WIDE_FEED_COLUMNS,
            tabletFeedColumns = this[Keys.tabletFeedColumns] ?: DEFAULT_WIDE_FEED_COLUMNS,
            catalogMode = CatalogMode.entries.firstOrNull { it.name == this[Keys.catalogMode] } ?: CatalogMode.GRID,
        )

    private fun Preferences.playbackPreferences(): PlaybackPreferences =
        this[Keys.playback]?.let { encoded ->
            runCatching { playbackJson.decodeFromString<PlaybackPreferences>(encoded) }.getOrNull()
        } ?: PlaybackPreferences()

    private val playbackJson = Json { ignoreUnknownKeys = true }

    private object Keys {
        val playback = stringPreferencesKey("playback_preferences")
        val hideNsfwBoards = booleanPreferencesKey("hide_nsfw_boards")
        val coverViolentMedia = booleanPreferencesKey("cover_violent_media")
        val deepMediaScan = booleanPreferencesKey("deep_media_scan")
        val openThreadComments = booleanPreferencesKey("open_thread_comments")
        val themeMode = stringPreferencesKey("theme_mode")
        val amoled = booleanPreferencesKey("amoled")
        val biometricLock = booleanPreferencesKey("biometric_lock")
        val screenPrivacy = booleanPreferencesKey("screen_privacy")
        val dohProvider = stringPreferencesKey("doh_provider")
        val strictDoh = booleanPreferencesKey("strict_doh")
        val saveSearchHistory = booleanPreferencesKey("save_search_history")
        val saveBrowsingHistory = booleanPreferencesKey("save_browsing_history")
        val activeProviderId = stringPreferencesKey("active_provider_id")
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val unfoldedFeedColumns = intPreferencesKey("unfolded_feed_columns")
        val tabletFeedColumns = intPreferencesKey("tablet_feed_columns")
        val catalogMode = stringPreferencesKey("catalog_mode")
    }
}
