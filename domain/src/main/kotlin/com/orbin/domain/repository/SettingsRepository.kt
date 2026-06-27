package com.orbin.domain.repository

import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import kotlinx.coroutines.flow.Flow

/** Reads and updates persisted [AppSettings]. Implemented in :data over DataStore. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: AppThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setAmoled(enabled: Boolean)

    suspend fun setFontScale(scale: Float)

    suspend fun setAutoplayVideos(enabled: Boolean)

    suspend fun setMuteByDefault(enabled: Boolean)

    suspend fun setPreloadImages(enabled: Boolean)

    suspend fun setDohEnabled(enabled: Boolean)

    suspend fun setHttpsOnly(enabled: Boolean)

    suspend fun setUserAgent(userAgent: String)
}
