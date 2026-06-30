package com.orbin.domain.repository

import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.DohProvider
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.ThumbnailSize
import kotlinx.coroutines.flow.Flow

/** Reads and updates persisted [AppSettings]. Implemented in :data over DataStore. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setPersonalizedHomeFeed(enabled: Boolean)

    suspend fun setHiddenTags(tags: String)

    suspend fun setMutedTags(tags: String)

    suspend fun setHideNsfwBoards(enabled: Boolean)

    suspend fun setHideTextOnlyThreads(enabled: Boolean)

    suspend fun setThemeMode(mode: AppThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setAmoled(enabled: Boolean)

    suspend fun setFontScale(scale: Float)

    suspend fun setThumbnailSize(size: ThumbnailSize)

    suspend fun setAutoplayVideos(enabled: Boolean)

    suspend fun setMuteByDefault(enabled: Boolean)

    suspend fun setPreloadImages(enabled: Boolean)

    suspend fun setFeedThreadLimit(limit: FeedThreadLimit)

    suspend fun setDownloadFolderUri(uri: String)

    suspend fun setDohEnabled(enabled: Boolean)

    suspend fun setDohProvider(provider: DohProvider)

    suspend fun setHttpsOnly(enabled: Boolean)

    suspend fun setBiometricLockEnabled(enabled: Boolean)

    suspend fun setSaveRecentSearches(enabled: Boolean)

    suspend fun setUserAgent(userAgent: String)

    suspend fun setOnboardingCompleted(completed: Boolean)
}
