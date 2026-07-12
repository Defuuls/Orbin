package com.orbin.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.AppIconVariant
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.ColorTheme
import com.orbin.core.model.DohProvider
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.PreloadOption
import com.orbin.core.model.PreloadThrottleMode
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThumbnailSize
import com.orbin.domain.repository.DownloadRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.SearchRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Exposes settings and update actions for the settings screen. */
@Suppress("TooManyFunctions")
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
        private val historyRepository: HistoryRepository,
        private val searchRepository: SearchRepository,
        private val downloadRepository: DownloadRepository,
        registry: ProviderRegistry,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings> =
            repository.settings
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings.Default)

        /** All registered providers the user can pick as active, in display order. */
        val providers: ImmutableList<ProviderMetadata> = registry.all().map { it.metadata }.toImmutableList()

        private val defaultProviderMetadata: ProviderMetadata = registry.default().metadata

        /** The provider currently selected as active, resolved against [providers]. */
        val activeProvider: StateFlow<ProviderMetadata> =
            settings
                .map { appSettings ->
                    providers.firstOrNull { it.id.value == appSettings.activeProviderId } ?: defaultProviderMetadata
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), defaultProviderMetadata)

        fun setActiveProvider(id: ProviderId) = update { repository.setActiveProviderId(id) }

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

        fun setAutoplay(enabled: Boolean) = update { repository.setAutoplayVideos(enabled) }

        fun setMute(enabled: Boolean) = update { repository.setMuteByDefault(enabled) }

        fun setFullscreenVideoPlayback(enabled: Boolean) = update { repository.setFullscreenVideoPlayback(enabled) }

        fun setAutoRotateVideoFullscreen(enabled: Boolean) = update { repository.setAutoRotateVideoFullscreen(enabled) }

        fun setPreload(enabled: Boolean) = update { repository.setPreloadImages(enabled) }

        fun setPreloadOption(option: PreloadOption) = update { repository.setPreloadOption(option) }

        fun setPreloadThrottleMode(mode: PreloadThrottleMode) = update { repository.setPreloadThrottleMode(mode) }

        fun setFeedThreadLimit(limit: FeedThreadLimit) = update { repository.setFeedThreadLimit(limit) }

        fun setDownloadFolderUri(uri: String) = update { repository.setDownloadFolderUri(uri) }

        fun setDoh(enabled: Boolean) = update { repository.setDohEnabled(enabled) }

        fun setDohProvider(provider: DohProvider) = update { repository.setDohProvider(provider) }

        fun setBiometricLock(enabled: Boolean) = update { repository.setBiometricLockEnabled(enabled) }

        fun setSaveRecentSearches(enabled: Boolean) = update { repository.setSaveRecentSearches(enabled) }

        fun setInternalUpdater(enabled: Boolean) = update { repository.setInternalUpdaterEnabled(enabled) }

        fun setColorTheme(theme: ColorTheme) = update { repository.setColorTheme(theme) }

        fun setAppIconVariant(variant: AppIconVariant) = update { repository.setAppIconVariant(variant) }

        fun setFullScreenFeedChrome(enabled: Boolean) = update { repository.setFullScreenFeedChrome(enabled) }

        fun setRefreshFeedOnReturn(enabled: Boolean) = update { repository.setRefreshFeedOnReturn(enabled) }

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
