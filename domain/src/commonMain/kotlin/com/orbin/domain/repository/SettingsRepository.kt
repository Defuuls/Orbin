package com.orbin.domain.repository

import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.CatalogMode
import com.orbin.core.model.DohProvider
import com.orbin.core.model.FormFactor
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.PlaybackSettings
import com.orbin.core.model.ProviderId
import kotlinx.coroutines.flow.Flow

/** Reads and updates persisted [AppSettings]. Implemented in :data over DataStore. */
@Suppress("TooManyFunctions")
interface SettingsRepository {
    val settings: Flow<AppSettings>

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

    /** Saves the on-disk video cache limit in megabytes. */
    suspend fun setVideoCacheMegabytes(megabytes: Int)
}
