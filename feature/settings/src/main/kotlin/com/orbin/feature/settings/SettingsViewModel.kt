package com.orbin.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.AppThemePalette
import com.orbin.core.model.DohProvider
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.ThumbnailSize
import com.orbin.core.model.VpnProvider
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

        fun setThemePalette(palette: AppThemePalette) = update { repository.setThemePalette(palette) }

        fun setDynamicColor(enabled: Boolean) = update { repository.setDynamicColor(enabled) }

        fun setAmoled(enabled: Boolean) = update { repository.setAmoled(enabled) }

        fun setFontScale(scale: Float) = update { repository.setFontScale(scale) }

        fun setThumbnailSize(size: ThumbnailSize) = update { repository.setThumbnailSize(size) }

        fun setAutoplay(enabled: Boolean) = update { repository.setAutoplayVideos(enabled) }

        fun setMute(enabled: Boolean) = update { repository.setMuteByDefault(enabled) }

        fun setPreload(enabled: Boolean) = update { repository.setPreloadImages(enabled) }

        fun setAutoDownloadFullThreadMedia(enabled: Boolean) =
            update { repository.setAutoDownloadFullThreadMedia(enabled) }

        fun setFeedThreadLimit(limit: FeedThreadLimit) = update { repository.setFeedThreadLimit(limit) }

        fun setDownloadFolderUri(uri: String) = update { repository.setDownloadFolderUri(uri) }

        fun setDoh(enabled: Boolean) = update { repository.setDohEnabled(enabled) }

        fun setDohProvider(provider: DohProvider) = update { repository.setDohProvider(provider) }

        fun setHttpsOnly(enabled: Boolean) = update { repository.setHttpsOnly(enabled) }

        fun setVpnProvider(provider: VpnProvider) = update { repository.setVpnProvider(provider) }

        fun setProxyHost(host: String) = update { repository.setProxyHost(host) }

        fun setProxyPort(port: String) = update { repository.setProxyPort(port) }

        fun setProxySocks(enabled: Boolean) = update { repository.setProxySocks(enabled) }

        fun setBiometricLock(enabled: Boolean) = update { repository.setBiometricLockEnabled(enabled) }

        fun setSaveRecentSearches(enabled: Boolean) = update { repository.setSaveRecentSearches(enabled) }

        private fun update(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
