package com.orbin.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.orbin.core.common.dispatchers.ApplicationScope
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
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
class SettingsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        @ApplicationScope scope: CoroutineScope,
    ) : SettingsRepository,
        NetworkConfigProvider {
        override val settings: Flow<AppSettings> = dataStore.data.map { it.toAppSettings() }

        private val cached = settings.stateIn(scope, SharingStarted.Eagerly, AppSettings.Default)

        override suspend fun setThemeMode(mode: AppThemeMode) = edit { it[Keys.themeMode] = mode.name }

        override suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.dynamicColor] = enabled }

        override suspend fun setAmoled(enabled: Boolean) = edit { it[Keys.amoled] = enabled }

        override suspend fun setFontScale(scale: Float) = edit { it[Keys.fontScale] = scale }

        override suspend fun setAutoplayVideos(enabled: Boolean) = edit { it[Keys.autoplay] = enabled }

        override suspend fun setMuteByDefault(enabled: Boolean) = edit { it[Keys.mute] = enabled }

        override suspend fun setPreloadImages(enabled: Boolean) = edit { it[Keys.preload] = enabled }

        override suspend fun setDohEnabled(enabled: Boolean) = edit { it[Keys.doh] = enabled }

        override suspend fun setHttpsOnly(enabled: Boolean) = edit { it[Keys.httpsOnly] = enabled }

        override suspend fun setUserAgent(userAgent: String) = edit { it[Keys.userAgent] = userAgent }

        override fun current(): NetworkConfig = cached.value.toNetworkConfig()

        private suspend fun edit(block: (Preferences) -> Unit) {
            dataStore.edit { block(it) }
        }

        private fun Preferences.toAppSettings(): AppSettings =
            AppSettings(
                themeMode = this[Keys.themeMode]?.let(AppThemeMode::valueOf) ?: AppThemeMode.SYSTEM,
                dynamicColor = this[Keys.dynamicColor] ?: true,
                amoled = this[Keys.amoled] ?: false,
                fontScale = this[Keys.fontScale] ?: 1f,
                autoplayVideos = this[Keys.autoplay] ?: false,
                muteByDefault = this[Keys.mute] ?: true,
                preloadImages = this[Keys.preload] ?: true,
                userAgent = this[Keys.userAgent] ?: "",
                dohEnabled = this[Keys.doh] ?: false,
                httpsOnly = this[Keys.httpsOnly] ?: true,
            )

        private fun AppSettings.toNetworkConfig(): NetworkConfig =
            NetworkConfig(
                userAgent = userAgent.ifBlank { NetworkConfig.DEFAULT_USER_AGENT },
                dnsOverHttps = if (dohEnabled) DohConfig.Cloudflare else DohConfig.Disabled,
                httpsOnly = httpsOnly,
            )

        private object Keys {
            val themeMode = stringPreferencesKey("theme_mode")
            val dynamicColor = booleanPreferencesKey("dynamic_color")
            val amoled = booleanPreferencesKey("amoled")
            val fontScale = floatPreferencesKey("font_scale")
            val autoplay = booleanPreferencesKey("autoplay_videos")
            val mute = booleanPreferencesKey("mute_by_default")
            val preload = booleanPreferencesKey("preload_images")
            val userAgent = stringPreferencesKey("user_agent")
            val doh = booleanPreferencesKey("doh_enabled")
            val httpsOnly = booleanPreferencesKey("https_only")
        }
    }
