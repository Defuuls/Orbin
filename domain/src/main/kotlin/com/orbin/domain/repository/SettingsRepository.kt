package com.orbin.domain.repository

import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.ColorTheme
import com.orbin.core.model.FeedSort
import com.orbin.core.model.ProviderId
import kotlinx.coroutines.flow.Flow

/** Reads and updates persisted [AppSettings]. Implemented in :data over DataStore. */
@Suppress("TooManyFunctions")
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setHideNsfwBoards(enabled: Boolean)

    suspend fun setDeepMediaScan(enabled: Boolean)

    suspend fun setThemeMode(mode: AppThemeMode)

    suspend fun setAmoled(enabled: Boolean)

    suspend fun setFontScale(scale: Float)

    suspend fun setMuteByDefault(enabled: Boolean)

    suspend fun setFeedSort(sort: FeedSort)

    suspend fun setDownloadFolderUri(uri: String)

    suspend fun setBiometricLockEnabled(enabled: Boolean)

    suspend fun setSaveRecentSearches(enabled: Boolean)

    suspend fun setInternalUpdaterEnabled(enabled: Boolean)

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setActiveProviderId(id: ProviderId)

    suspend fun setColorTheme(theme: ColorTheme)
}
