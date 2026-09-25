package com.orbin.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.BoardId
import com.orbin.core.model.FeedThreadLimit
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
        BoardPreferencesRepository,
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

        override fun observeFeedThreadLimit(
            provider: ProviderId,
            board: BoardId,
        ): Flow<FeedThreadLimit?> =
            dataStore.data.map { preferences ->
                preferences[Keys.boardFeedThreadLimit(provider, board)]?.let {
                    runCatching { enumValueOf<FeedThreadLimit>(it) }.getOrNull()
                }
            }

        override fun observeFeedThreadLimits(
            provider: ProviderId,
            boards: Set<BoardId>,
        ): Flow<Map<BoardId, FeedThreadLimit>> =
            dataStore.data.map { preferences ->
                boards
                    .mapNotNull { board ->
                        preferences[Keys.boardFeedThreadLimit(provider, board)]
                            ?.let { stored -> runCatching { enumValueOf<FeedThreadLimit>(stored) }.getOrNull() }
                            ?.let { limit -> board to limit }
                    }.toMap()
            }

        override suspend fun setFeedThreadLimit(
            provider: ProviderId,
            board: BoardId,
            limit: FeedThreadLimit?,
        ) {
            edit { preferences ->
                val key = Keys.boardFeedThreadLimit(provider, board)
                if (limit != null) preferences[key] = limit.name else preferences.remove(key)
            }
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

            fun favoriteBoards(provider: ProviderId): Preferences.Key<Set<String>> =
                stringSetPreferencesKey("favorite_boards_${provider.value}")

            fun subscribedBoards(provider: ProviderId): Preferences.Key<Set<String>> =
                stringSetPreferencesKey("subscribed_boards_${provider.value}")

            fun boardFeedThreadLimit(
                provider: ProviderId,
                board: BoardId,
            ): Preferences.Key<String> = stringPreferencesKey("feed_thread_limit_${provider.value}_${board.value}")
        }
    }

private val FIXED_NETWORK_CONFIG = NetworkConfig()
