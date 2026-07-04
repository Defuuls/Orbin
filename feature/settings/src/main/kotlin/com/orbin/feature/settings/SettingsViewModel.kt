package com.orbin.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.DohProvider
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.ThumbnailSize
import com.orbin.domain.repository.DownloadRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.SearchRepository
import com.orbin.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Exposes settings and update actions for the settings screen. */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
        private val historyRepository: HistoryRepository,
        private val searchRepository: SearchRepository,
        private val downloadRepository: DownloadRepository,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings> =
            repository.settings
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings.Default)

        fun setPersonalizedHomeFeed(enabled: Boolean) = update { repository.setPersonalizedHomeFeed(enabled) }

        fun setHiddenTags(tags: String) = update { repository.setHiddenTags(tags) }

        fun setMutedTags(tags: String) = update { repository.setMutedTags(tags) }

        fun setHideNsfwBoards(enabled: Boolean) = update { repository.setHideNsfwBoards(enabled) }

        fun setHideTextOnlyThreads(enabled: Boolean) = update { repository.setHideTextOnlyThreads(enabled) }

        fun setThemeMode(mode: AppThemeMode) = update { repository.setThemeMode(mode) }

        fun setDynamicColor(enabled: Boolean) = update { repository.setDynamicColor(enabled) }

        fun setAmoled(enabled: Boolean) = update { repository.setAmoled(enabled) }

        fun setFontScale(scale: Float) = update { repository.setFontScale(scale) }

        fun setThumbnailSize(size: ThumbnailSize) = update { repository.setThumbnailSize(size) }

        fun setOneHandedMode(enabled: Boolean) = update { repository.setOneHandedModeEnabled(enabled) }

        fun setAutoplay(enabled: Boolean) = update { repository.setAutoplayVideos(enabled) }

        fun setMute(enabled: Boolean) = update { repository.setMuteByDefault(enabled) }

        fun setPreload(enabled: Boolean) = update { repository.setPreloadImages(enabled) }

        fun setFeedThreadLimit(limit: FeedThreadLimit) = update { repository.setFeedThreadLimit(limit) }

        fun setDownloadFolderUri(uri: String) = update { repository.setDownloadFolderUri(uri) }

        fun setDoh(enabled: Boolean) = update { repository.setDohEnabled(enabled) }

        fun setDohProvider(provider: DohProvider) = update { repository.setDohProvider(provider) }

        fun setBiometricLock(enabled: Boolean) = update { repository.setBiometricLockEnabled(enabled) }

        fun setSaveRecentSearches(enabled: Boolean) = update { repository.setSaveRecentSearches(enabled) }

        fun clearLocalActivity() =
            update {
                historyRepository.clear()
                searchRepository.clearRecentQueries()
                downloadRepository.clearHistory()
            }

        private fun update(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
