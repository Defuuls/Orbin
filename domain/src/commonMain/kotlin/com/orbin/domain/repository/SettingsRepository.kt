package com.orbin.domain.repository

import com.orbin.core.model.AccentTheme
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.CatalogMode
import com.orbin.core.model.DohProvider
import com.orbin.core.model.FormFactor
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.PlaybackSettings
import com.orbin.core.model.PostFilterRule
import com.orbin.core.model.ProviderId
import com.orbin.core.model.SharpPreviewsMode
import com.orbin.core.model.ThreadLayout
import kotlinx.coroutines.flow.Flow

/** Reads and updates persisted [AppSettings]. Implemented in :data over DataStore. */
@Suppress("TooManyFunctions")
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setAccentTheme(value: AccentTheme)

    suspend fun setThreadLayout(value: ThreadLayout)

    suspend fun setAutoScrollToLastSeen(value: Boolean)

    suspend fun setCollectionNewestFirst(value: Boolean)

    suspend fun setShowStickyThreads(value: Boolean)

    suspend fun setNsfwEachSession(value: Boolean)

    suspend fun setCoverNsfwMedia(value: Boolean)

    suspend fun setHistorySessionOnly(value: Boolean)

    suspend fun setPrivateSavedFiles(value: Boolean)

    suspend fun setAskBeforePhotos(value: Boolean)

    suspend fun setShowSavedMediaTab(value: Boolean)

    suspend fun setFullscreenAutoAdvance(value: Boolean)

    suspend fun setHiddenThreadPatterns(value: List<String>)

    suspend fun setPostFilterRules(value: List<PostFilterRule>)

    /** Saves a scoped playback snapshot, or removes its override to resume inheritance. */
    suspend fun setPlaybackSettings(
        scope: PlaybackScope,
        settings: PlaybackSettings?,
    )

    suspend fun setHideNsfwBoards(enabled: Boolean)

    suspend fun setCoverViolentMedia(enabled: Boolean)

    suspend fun setDeepMediaScan(enabled: Boolean)

    suspend fun setOpenThreadComments(enabled: Boolean)

    suspend fun setThemeMode(mode: AppThemeMode)

    suspend fun setAmoled(enabled: Boolean)

    suspend fun setBiometricLockEnabled(enabled: Boolean)

    suspend fun setScreenPrivacyEnabled(enabled: Boolean)

    suspend fun setDohProvider(provider: DohProvider)

    suspend fun setStrictDohEnabled(enabled: Boolean)

    suspend fun setSaveSearchHistory(enabled: Boolean)

    suspend fun setSaveBrowsingHistory(enabled: Boolean)

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setActiveProviderId(id: ProviderId)

    /** Saves the feed's column count for [formFactor]'s wide screen. A phone has no choice. */
    suspend fun setFeedColumns(
        formFactor: FormFactor,
        columns: Int,
    )

    /** Saves the device-wide board catalog presentation. */
    suspend fun setCatalogMode(mode: CatalogMode)

    /** Saves whether card and grid views may load full-size still images. */
    suspend fun setSharpPreviews(mode: SharpPreviewsMode)

    /** Saves the on-disk video cache limit in megabytes. */
    suspend fun setVideoCacheMegabytes(megabytes: Int)
}
