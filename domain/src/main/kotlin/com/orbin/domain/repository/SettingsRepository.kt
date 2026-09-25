package com.orbin.domain.repository

import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.ColorTheme
import com.orbin.core.model.DohProvider
import com.orbin.core.model.DownloadOrganization
import com.orbin.core.model.FeedSort
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.MediaFilter
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadPresentation
import com.orbin.core.model.ThumbnailSize
import kotlinx.coroutines.flow.Flow

/** Reads and updates persisted [AppSettings]. Implemented in :data over DataStore. */
@Suppress("TooManyFunctions")
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setHiddenTags(tags: String)

    suspend fun setMutedTags(tags: String)

    suspend fun setHideNsfwBoards(enabled: Boolean)

    suspend fun setHideTextOnlyThreads(enabled: Boolean)

    suspend fun setHarshContentFilter(enabled: Boolean)

    suspend fun setDeepMediaScan(enabled: Boolean)

    suspend fun setMediaFilter(filter: MediaFilter)

    suspend fun setThreadPresentation(presentation: ThreadPresentation)

    suspend fun setThemeMode(mode: AppThemeMode)

    suspend fun setAmoled(enabled: Boolean)

    suspend fun setFontScale(scale: Float)

    suspend fun setThumbnailSize(size: ThumbnailSize)

    suspend fun setMuteByDefault(enabled: Boolean)

    suspend fun setFullscreenVideoPlayback(enabled: Boolean)

    suspend fun setAutoRotateVideoFullscreen(enabled: Boolean)

    suspend fun setFeedThreadLimit(limit: FeedThreadLimit)

    suspend fun setFeedSort(sort: FeedSort)

    suspend fun setImageCacheLimitMb(megabytes: Int)

    suspend fun setDownloadFolderUri(uri: String)

    suspend fun setDownloadOrganization(organization: DownloadOrganization)

    suspend fun setDohProvider(provider: DohProvider)

    suspend fun setBiometricLockEnabled(enabled: Boolean)

    suspend fun setSaveRecentSearches(enabled: Boolean)

    suspend fun setInternalUpdaterEnabled(enabled: Boolean)

    suspend fun setUserAgent(userAgent: String)

    suspend fun setConnectTimeoutSeconds(seconds: Long)

    suspend fun setReadTimeoutSeconds(seconds: Long)

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setActiveProviderId(id: ProviderId)

    suspend fun setColorTheme(theme: ColorTheme)

    suspend fun setFullScreenFeedChrome(enabled: Boolean)

    suspend fun setThreadWatchNotificationsEnabled(enabled: Boolean)

    suspend fun setQuietHoursStart(time: String)

    suspend fun setQuietHoursEnd(time: String)

    suspend fun setMediaScrollThreadView(enabled: Boolean)

    suspend fun setMediaScrollBoardView(enabled: Boolean)
}
