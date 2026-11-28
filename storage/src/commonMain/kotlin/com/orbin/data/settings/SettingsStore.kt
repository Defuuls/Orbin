package com.orbin.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.orbin.core.model.AccentTheme
import com.orbin.core.model.AppInterface
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.CatalogMode
import com.orbin.core.model.DEFAULT_VIDEO_CACHE_MEGABYTES
import com.orbin.core.model.DEFAULT_WIDE_FEED_COLUMNS
import com.orbin.core.model.DohProvider
import com.orbin.core.model.FormFactor
import com.orbin.core.model.PlaybackPreferences
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.PlaybackSettings
import com.orbin.core.model.PostFilterRule
import com.orbin.core.model.ProviderId
import com.orbin.core.model.SharpPreviewsMode
import com.orbin.core.model.ThreadLayout
import com.orbin.domain.privacy.PrivacySession
import com.orbin.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
    override val settings: Flow<AppSettings> =
        flow {
            // The first emission used by NextTheme must already have the leftover skin key gone.
            initializePrivacyDefaults()
            LegacyThemeMigration.clear(dataStore)
            emitAll(
                combine(dataStore.data, PrivacySession.nsfwRevealed) { prefs, revealed ->
                    prefs.toAppSettings().copy(nsfwSessionRevealed = revealed)
                },
            )
        }

    override suspend fun setAccentTheme(value: AccentTheme) {
        edit { it[Keys.accentTheme] = value.name }
    }

    override suspend fun setThreadLayout(value: ThreadLayout) {
        edit { it[Keys.threadLayout] = value.name }
    }

    override suspend fun setAutoScrollToLastSeen(value: Boolean) {
        edit { it[Keys.autoScrollToLastSeen] = value }
    }

    override suspend fun setCollectionNewestFirst(value: Boolean) {
        edit { it[Keys.collectionNewestFirst] = value }
    }

    override suspend fun setShowStickyThreads(value: Boolean) {
        edit { it[Keys.showStickyThreads] = value }
    }

    override suspend fun setNsfwEachSession(value: Boolean) {
        edit { it[Keys.nsfwEachSession] = value }
    }

    override suspend fun setCoverNsfwMedia(value: Boolean) {
        edit { it[Keys.coverNsfwMedia] = value }
    }

    override suspend fun setHistorySessionOnly(value: Boolean) {
        edit { it[Keys.historySessionOnly] = value }
    }

    override suspend fun setPrivateSavedFiles(value: Boolean) {
        edit { it[Keys.privateSavedFiles] = value }
    }

    override suspend fun setAskBeforePhotos(value: Boolean) {
        edit { it[Keys.askBeforePhotos] = value }
    }

    override suspend fun setShowSavedMediaTab(value: Boolean) {
        edit { it[Keys.showSavedMediaTab] = value }
    }

    override suspend fun setFullscreenAutoAdvance(value: Boolean) {
        edit { it[Keys.fullscreenAutoAdvance] = value }
    }

    override suspend fun setHiddenThreadPatterns(value: List<String>) {
        edit { it[Keys.hiddenThreadPatterns] = playbackJson.encodeToString(value.distinct()) }
    }

    override suspend fun setPostFilterRules(value: List<PostFilterRule>) {
        edit { it[Keys.postFilterRules] = playbackJson.encodeToString(value.distinct()) }
    }

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

    override suspend fun setAppInterface(value: AppInterface) {
        edit { it[Keys.appInterface] = value.name }
    }

    override suspend fun setBiometricLockEnabled(enabled: Boolean) {
        edit { it[Keys.biometricLock] = enabled }
    }

    override suspend fun setScreenPrivacyEnabled(enabled: Boolean) {
        edit { it[Keys.screenPrivacy] = enabled }
    }

    override suspend fun setOpenLinksInApp(enabled: Boolean) {
        edit { it[Keys.openLinksInApp] = enabled }
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

    override suspend fun setSharpPreviews(mode: SharpPreviewsMode) {
        edit { it[Keys.sharpPreviews] = mode.name }
    }

    override suspend fun setVideoCacheMegabytes(megabytes: Int) {
        edit { it[Keys.videoCacheMegabytes] = megabytes.coerceAtLeast(0) }
    }

    private suspend fun initializePrivacyDefaults() {
        dataStore.edit { prefs ->
            if (PRIVACY_DEFAULTS_MIGRATED !in prefs) {
                // Existing stores keep the former implicit false; a fresh install starts SFW.
                if (prefs.asMap().isEmpty()) prefs[Keys.hideNsfwBoards] = true
                prefs[PRIVACY_DEFAULTS_MIGRATED] = true
            }
        }
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        initializePrivacyDefaults()
        dataStore.edit { block(it) }
    }

    @Suppress("ComplexMethod")
    private fun Preferences.toAppSettings(): AppSettings =
        AppSettings(
            accentTheme = storedAccentTheme(this[Keys.accentTheme]),
            threadLayout =
                ThreadLayout.entries.firstOrNull { it.name == this[Keys.threadLayout] } ?: ThreadLayout.NESTED,
            autoScrollToLastSeen = this[Keys.autoScrollToLastSeen] ?: true,
            showStickyThreads = this[Keys.showStickyThreads] ?: true,
            collectionNewestFirst = this[Keys.collectionNewestFirst] ?: true,
            nsfwEachSession = this[Keys.nsfwEachSession] ?: false,
            coverNsfwMedia = this[Keys.coverNsfwMedia] ?: false,
            historySessionOnly = this[Keys.historySessionOnly] ?: false,
            privateSavedFiles = this[Keys.privateSavedFiles] ?: false,
            askBeforePhotos = this[Keys.askBeforePhotos] ?: false,
            showSavedMediaTab = this[Keys.showSavedMediaTab] ?: true,
            fullscreenAutoAdvance = this[Keys.fullscreenAutoAdvance] ?: false,
            hiddenThreadPatterns =
                this[Keys.hiddenThreadPatterns]?.let {
                    runCatching {
                        playbackJson.decodeFromString<List<String>>(
                            it,
                        )
                    }.getOrNull()
                }
                    ?: emptyList(),
            postFilterRules =
                this[Keys.postFilterRules]?.let {
                    runCatching {
                        playbackJson.decodeFromString<List<PostFilterRule>>(
                            it,
                        )
                    }.getOrNull()
                }
                    ?: emptyList(),
            playbackPreferences = playbackPreferences(),
            hideNsfwBoards = this[Keys.hideNsfwBoards] ?: false,
            coverViolentMedia = this[Keys.coverViolentMedia] ?: true,
            deepMediaScan = this[Keys.deepMediaScan] ?: false,
            openThreadComments = this[Keys.openThreadComments] ?: false,
            themeMode = AppThemeMode.entries.firstOrNull { it.name == this[Keys.themeMode] } ?: AppThemeMode.SYSTEM,
            amoled = this[Keys.amoled] ?: false,
            appInterface =
                AppInterface.entries.firstOrNull { it.name == this[Keys.appInterface] } ?: AppInterface.CORAL,
            biometricLockEnabled = this[Keys.biometricLock] ?: false,
            screenPrivacyEnabled = this[Keys.screenPrivacy] ?: false,
            openLinksInApp = this[Keys.openLinksInApp] ?: true,
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
            sharpPreviews =
                SharpPreviewsMode.entries.firstOrNull { it.name == this[Keys.sharpPreviews] }
                    ?: SharpPreviewsMode.WIFI_ONLY,
            videoCacheMegabytes = this[Keys.videoCacheMegabytes] ?: DEFAULT_VIDEO_CACHE_MEGABYTES,
        )

    private fun Preferences.playbackPreferences(): PlaybackPreferences =
        this[Keys.playback]?.let { encoded ->
            runCatching { playbackJson.decodeFromString<PlaybackPreferences>(encoded) }.getOrNull()
        } ?: PlaybackPreferences()

    private val playbackJson = Json { ignoreUnknownKeys = true }

    private object Keys {
        val accentTheme = stringPreferencesKey("accent_theme")
        val threadLayout = stringPreferencesKey("thread_layout")
        val autoScrollToLastSeen = booleanPreferencesKey("auto_scroll_to_last_seen")
        val collectionNewestFirst = booleanPreferencesKey("collection_newest_first")
        val showStickyThreads = booleanPreferencesKey("show_sticky_threads")
        val nsfwEachSession = booleanPreferencesKey("nsfw_each_session")
        val coverNsfwMedia = booleanPreferencesKey("cover_nsfw_media")
        val historySessionOnly = booleanPreferencesKey("history_session_only")
        val privateSavedFiles = booleanPreferencesKey("private_saved_files")
        val askBeforePhotos = booleanPreferencesKey("ask_before_photos")
        val showSavedMediaTab = booleanPreferencesKey("show_saved_media_tab")
        val fullscreenAutoAdvance = booleanPreferencesKey("fullscreen_auto_advance")
        val hiddenThreadPatterns = stringPreferencesKey("hidden_thread_patterns")
        val postFilterRules = stringPreferencesKey("post_filter_rules")
        val playback = stringPreferencesKey("playback_preferences")
        val hideNsfwBoards = booleanPreferencesKey("hide_nsfw_boards")
        val coverViolentMedia = booleanPreferencesKey("cover_violent_media")
        val deepMediaScan = booleanPreferencesKey("deep_media_scan")
        val openThreadComments = booleanPreferencesKey("open_thread_comments")
        val themeMode = stringPreferencesKey("theme_mode")
        val amoled = booleanPreferencesKey("amoled")
        val appInterface = stringPreferencesKey("app_interface")
        val biometricLock = booleanPreferencesKey("biometric_lock")
        val screenPrivacy = booleanPreferencesKey("screen_privacy")
        val openLinksInApp = booleanPreferencesKey("open_links_in_app")
        val dohProvider = stringPreferencesKey("doh_provider")
        val strictDoh = booleanPreferencesKey("strict_doh")
        val saveSearchHistory = booleanPreferencesKey("save_search_history")
        val saveBrowsingHistory = booleanPreferencesKey("save_browsing_history")
        val activeProviderId = stringPreferencesKey("active_provider_id")
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val unfoldedFeedColumns = intPreferencesKey("unfolded_feed_columns")
        val tabletFeedColumns = intPreferencesKey("tablet_feed_columns")
        val catalogMode = stringPreferencesKey("catalog_mode")
        val sharpPreviews = stringPreferencesKey("sharp_previews")
        val videoCacheMegabytes = intPreferencesKey("video_cache_megabytes")
    }
}

private val PRIVACY_DEFAULTS_MIGRATED = booleanPreferencesKey("privacy_defaults_migrated")

/**
 * The saved accent by its stable name. The picker's separate Orange became Orbin's default accent, so a
 * saved `ORANGE` (and anything unknown) reads as [AccentTheme.ORBIN].
 */
internal fun storedAccentTheme(name: String?): AccentTheme =
    AccentTheme.entries.firstOrNull { it.name == name } ?: AccentTheme.ORBIN
