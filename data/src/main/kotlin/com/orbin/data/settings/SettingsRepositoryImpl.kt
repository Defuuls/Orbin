package com.orbin.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.orbin.core.common.dispatchers.ApplicationScope
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.BoardId
import com.orbin.core.model.DohProvider
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThumbnailSize
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.network.DohConfig
import com.orbin.network.NetworkConfig
import com.orbin.network.NetworkConfigProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SettingsRepository] persisted with DataStore Preferences. Also implements
 * [NetworkConfigProvider]: it keeps a hot [stateIn] cache of the current settings so the
 * (synchronous) `current()` call the OkHttp graph needs can read the latest values without
 * blocking.
 */
@Singleton
@Suppress("TooManyFunctions")
class SettingsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        @ApplicationScope scope: CoroutineScope,
    ) : SettingsRepository,
        BoardPreferencesRepository,
        NetworkConfigProvider {
        override val settings: Flow<AppSettings> = dataStore.data.map { it.toAppSettings() }

        private val cached = settings.stateIn(scope, SharingStarted.Eagerly, AppSettings.Default)

        override suspend fun setPersonalizedHomeFeed(enabled: Boolean) =
            edit { it[Keys.personalizedHomeFeed] = enabled }

        override suspend fun setHiddenTags(tags: String) =
            edit { it[Keys.hiddenTags] = tags }

        override suspend fun setMutedTags(tags: String) =
            edit { it[Keys.mutedTags] = tags }

        override suspend fun setHideNsfwBoards(enabled: Boolean) =
            edit { it[Keys.hideNsfwBoards] = enabled }

        override suspend fun setHideTextOnlyThreads(enabled: Boolean) =
            edit { it[Keys.hideTextOnlyThreads] = enabled }

        override suspend fun setThemeMode(mode: AppThemeMode) =
            edit { it[Keys.themeMode] = mode.name }

        override suspend fun setDynamicColor(enabled: Boolean) =
            edit { it[Keys.dynamicColor] = enabled }

        override suspend fun setAmoled(enabled: Boolean) =
            edit { it[Keys.amoled] = enabled }

        override suspend fun setFontScale(scale: Float) =
            edit { it[Keys.fontScale] = scale }

        override suspend fun setThumbnailSize(size: ThumbnailSize) =
            edit { it[Keys.thumbnailSize] = size.name }

        override suspend fun setAutoplayVideos(enabled: Boolean) =
            edit { it[Keys.autoplay] = enabled }

        override suspend fun setMuteByDefault(enabled: Boolean) =
            edit { it[Keys.mute] = enabled }

        override suspend fun setPreloadImages(enabled: Boolean) =
            edit { it[Keys.preload] = enabled }

        override suspend fun setFeedThreadLimit(limit: FeedThreadLimit) =
            edit { it[Keys.feedThreadLimit] = limit.name }

        override suspend fun setDownloadFolderUri(uri: String) =
            edit { it[Keys.downloadFolderUri] = uri }

        override suspend fun setDohEnabled(enabled: Boolean) =
            edit { it[Keys.doh] = enabled }

        override suspend fun setDohProvider(provider: DohProvider) =
            edit { it[Keys.dohProvider] = provider.name }

        override suspend fun setHttpsOnly(enabled: Boolean) =
            edit { it[Keys.httpsOnly] = true }

        override suspend fun setBiometricLockEnabled(enabled: Boolean) =
            edit { it[Keys.biometricLock] = enabled }

        override suspend fun setSaveRecentSearches(enabled: Boolean) =
            edit { it[Keys.saveRecentSearches] = enabled }

        override suspend fun setUserAgent(userAgent: String) =
            edit { it[Keys.userAgent] = userAgent }

        override suspend fun setOnboardingCompleted(completed: Boolean) =
            edit { it[Keys.onboardingCompleted] = completed }

        override fun observeFavoriteBoards(provider: ProviderId): Flow<Set<BoardId>> =
            dataStore.data.map { preferences ->
                preferences[Keys.favoriteBoards(provider)].orEmpty().map(::BoardId).toSet()
            }

        override fun observeSubscribedBoards(provider: ProviderId): Flow<Set<BoardId>> =
            dataStore.data.map { preferences ->
                preferences[Keys.subscribedBoards(provider)].orEmpty().map(::BoardId).toSet()
            }

        override suspend fun setFavoriteBoard(
            provider: ProviderId,
            board: BoardId,
            favorite: Boolean,
        ) = setBoardFlag(Keys.favoriteBoards(provider), board, favorite)

        override suspend fun setSubscribedBoard(
            provider: ProviderId,
            board: BoardId,
            subscribed: Boolean,
        ) = setBoardFlag(Keys.subscribedBoards(provider), board, subscribed)

        private suspend fun setBoardFlag(
            key: Preferences.Key<Set<String>>,
            board: BoardId,
            enabled: Boolean,
        ) {
            edit { preferences ->
                val current = preferences[key].orEmpty()
                preferences[key] =
                    if (enabled) current + board.value else current - board.value
            }
        }

        override fun current(): NetworkConfig = cached.value.toNetworkConfig()

        private suspend fun edit(block: (MutablePreferences) -> Unit) {
            dataStore.edit { block(it) }
        }

        private fun Preferences.toAppSettings(): AppSettings =
            AppSettings(
                personalizedHomeFeed = this[Keys.personalizedHomeFeed] ?: true,
                hiddenTags = this[Keys.hiddenTags] ?: "",
                mutedTags = this[Keys.mutedTags] ?: "",
                hideNsfwBoards = this[Keys.hideNsfwBoards] ?: false,
                hideTextOnlyThreads = this[Keys.hideTextOnlyThreads] ?: false,
                themeMode = this[Keys.themeMode]?.let(AppThemeMode::valueOf) ?: AppThemeMode.SYSTEM,
                dynamicColor = this[Keys.dynamicColor] ?: true,
                amoled = this[Keys.amoled] ?: false,
                fontScale = this[Keys.fontScale] ?: 1f,
                thumbnailSize =
                    this[Keys.thumbnailSize]?.toEnumOrDefault(ThumbnailSize.MEDIUM)
                        ?: ThumbnailSize.MEDIUM,
                autoplayVideos = this[Keys.autoplay] ?: false,
                muteByDefault = this[Keys.mute] ?: true,
                preloadImages = this[Keys.preload] ?: true,
                feedThreadLimit =
                    this[Keys.feedThreadLimit]
                        ?.toEnumOrDefault(FeedThreadLimit.TWELVE)
                        ?: FeedThreadLimit.TWELVE,
                downloadFolderUri = this[Keys.downloadFolderUri] ?: "",
                userAgent = this[Keys.userAgent] ?: "",
                dohEnabled = this[Keys.doh] ?: false,
                dohProvider =
                    this[Keys.dohProvider]?.toEnumOrDefault(DohProvider.CLOUDFLARE)
                        ?: DohProvider.CLOUDFLARE,
                httpsOnly = true,
                biometricLockEnabled = this[Keys.biometricLock] ?: false,
                saveRecentSearches = this[Keys.saveRecentSearches] ?: false,
                onboardingCompleted = this[Keys.onboardingCompleted] ?: false,
            )

        private fun AppSettings.toNetworkConfig(): NetworkConfig =
            NetworkConfig(
                userAgent = userAgent.ifBlank { NetworkConfig.DEFAULT_USER_AGENT },
                dnsOverHttps = if (dohEnabled) dohProvider.toDohConfig() else DohConfig.Disabled,
                httpsOnly = httpsOnly,
            )

        private fun DohProvider.toDohConfig(): DohConfig =
            when (this) {
                DohProvider.CLOUDFLARE -> DohConfig.Cloudflare
                DohProvider.OPENDNS -> DohConfig.OpenDns
                DohProvider.NEXTDNS -> DohConfig.NextDns
            }

        private inline fun <reified T : Enum<T>> String.toEnumOrDefault(default: T): T =
            runCatching { enumValueOf<T>(this) }.getOrDefault(default)

        private object Keys {
            val personalizedHomeFeed = booleanPreferencesKey("personalized_home_feed")
            val hiddenTags = stringPreferencesKey("hidden_tags")
            val mutedTags = stringPreferencesKey("muted_tags")
            val hideNsfwBoards = booleanPreferencesKey("hide_nsfw_boards")
            val hideTextOnlyThreads = booleanPreferencesKey("hide_text_only_threads")
            val themeMode = stringPreferencesKey("theme_mode")
            val dynamicColor = booleanPreferencesKey("dynamic_color")
            val amoled = booleanPreferencesKey("amoled")
            val fontScale = floatPreferencesKey("font_scale")
            val thumbnailSize = stringPreferencesKey("thumbnail_size")
            val autoplay = booleanPreferencesKey("autoplay_videos")
            val mute = booleanPreferencesKey("mute_by_default")
            val preload = booleanPreferencesKey("preload_images")
            val feedThreadLimit = stringPreferencesKey("feed_thread_limit")
            val downloadFolderUri = stringPreferencesKey("download_folder_uri")
            val userAgent = stringPreferencesKey("user_agent")
            val doh = booleanPreferencesKey("doh_enabled")
            val dohProvider = stringPreferencesKey("doh_provider")
            val httpsOnly = booleanPreferencesKey("https_only")
            val biometricLock = booleanPreferencesKey("biometric_lock")
            val saveRecentSearches = booleanPreferencesKey("save_recent_searches")
            val onboardingCompleted = booleanPreferencesKey("onboarding_completed")

            fun favoriteBoards(provider: ProviderId) =
                stringSetPreferencesKey("favorite_boards_${provider.value}")

            fun subscribedBoards(provider: ProviderId) =
                stringSetPreferencesKey("subscribed_boards_${provider.value}")
        }
    }
