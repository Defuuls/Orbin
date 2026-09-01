package com.orbin.minimal

import android.content.Context
import androidx.core.content.edit
import com.orbin.core.common.dispatchers.ApplicationScope
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.FeedRefreshInterval
import com.orbin.core.model.FeedSort
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.MediaFilter
import com.orbin.core.model.PreloadOption
import com.orbin.core.model.PreloadThrottleMode
import com.orbin.core.model.ThreadPresentation
import com.orbin.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Versioned application of the settings contract used by shared Minimal screens. */
@Singleton
class MinimalExperiencePolicy
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val settingsRepository: SettingsRepository,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        private val _ready = MutableStateFlow(false)
        val ready: StateFlow<Boolean> = _ready.asStateFlow()

        fun start() {
            if (_ready.value) return
            applicationScope.launch {
                if (preferences.getInt(KEY_PROFILE_VERSION, 0) < PROFILE_VERSION) {
                    applyMinimalExperienceProfile(settingsRepository)
                    preferences.edit { putInt(KEY_PROFILE_VERSION, PROFILE_VERSION) }
                }
                _ready.value = true
            }
        }

        private companion object {
            const val PREFERENCES_NAME = "minimal_experience_policy"
            const val KEY_PROFILE_VERSION = "profile_version"
            const val PROFILE_VERSION = 1
        }
    }

/**
 * Pins every shared-screen behavior Minimal intentionally supports.
 *
 * This function is deliberately independent of Android storage so the contract can be regression
 * tested against a fake repository. Values are explicit rather than copied from AppSettings.Default.
 */
internal suspend fun applyMinimalExperienceProfile(settingsRepository: SettingsRepository) {
    settingsRepository.setThemeMode(AppThemeMode.SYSTEM)
    settingsRepository.setDynamicColor(true)
    settingsRepository.setFontScale(1f)

    settingsRepository.setHideNsfwBoards(false)
    settingsRepository.setHideTextOnlyThreads(false)
    settingsRepository.setHarshContentFilter(false)
    settingsRepository.setMediaFilter(MediaFilter.ALL)
    settingsRepository.setFeedRefreshInterval(FeedRefreshInterval.FIVE_MINUTES)
    settingsRepository.setFeedThreadLimit(FeedThreadLimit.TWELVE)
    settingsRepository.setFeedSort(FeedSort.BOARD)

    settingsRepository.setThreadPresentation(ThreadPresentation.PAGE)
    settingsRepository.setAutoplayVideos(false)
    settingsRepository.setAutoplayVideosInFeed(false)
    settingsRepository.setMuteByDefault(true)
    settingsRepository.setFullscreenVideoPlayback(false)
    settingsRepository.setAutoRotateVideoFullscreen(false)
    settingsRepository.setMediaScrollThreadView(true)

    settingsRepository.setPreloadImages(true)
    settingsRepository.setPreloadOption(PreloadOption.IMAGES)
    settingsRepository.setPreloadThrottleMode(PreloadThrottleMode.MODERATE)

    settingsRepository.setThreadWatchNotificationsEnabled(false)
    settingsRepository.setSaveRecentSearches(false)
    settingsRepository.setInternalUpdaterEnabled(false)
}
