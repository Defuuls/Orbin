package com.orbin.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.network.DnsPrivacyMonitor
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.ColorTheme
import com.orbin.core.model.DohProvider
import com.orbin.core.model.DownloadOrganization
import com.orbin.core.model.FeedRefreshInterval
import com.orbin.core.model.FeedSort
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.MediaFilter
import com.orbin.core.model.PreloadOption
import com.orbin.core.model.PreloadThrottleMode
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadPresentation
import com.orbin.core.model.ThumbnailSize
import com.orbin.core.model.UpdateStatus
import com.orbin.domain.repository.DiagnosticsRepository
import com.orbin.domain.repository.DownloadRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.ImageCacheRepository
import com.orbin.domain.repository.SearchRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.repository.UpdateRepository
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.api.ProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        private val backupService: BackupService,
        private val updateRepository: UpdateRepository,
        private val diagnosticsRepository: DiagnosticsRepository,
        dnsPrivacyMonitor: DnsPrivacyMonitor,
        registry: ProviderRegistry,
        private val imageCacheRepository: ImageCacheRepository = EmptyImageCacheRepository,
    ) : ViewModel() {
        private val _backupStatus = MutableStateFlow<BackupStatus?>(null)
        private val _updateCheck = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
        private val _diagnosticsStatus = MutableStateFlow<DiagnosticsStatus?>(null)
        private val _imageCacheUsageBytes = MutableStateFlow(0L)

        val imageCacheUsageBytes: StateFlow<Long> = _imageCacheUsageBytes.asStateFlow()

        /** Result of the last diagnostics export or clear, for a snackbar. */
        val diagnosticsStatus: StateFlow<DiagnosticsStatus?> = _diagnosticsStatus.asStateFlow()

        /** State of a manual update check, for the button and its result message. */
        val updateCheck: StateFlow<UpdateCheckState> = _updateCheck.asStateFlow()

        /** Result of the last export or import, for a snackbar. Cleared by [clearBackupStatus]. */
        val backupStatus: StateFlow<BackupStatus?> = _backupStatus.asStateFlow()

        /**
         * True while DNS lookups are going through the system resolver because the chosen DoH
         * resolver is unreachable. Encrypted DNS cannot be switched off, so this is the only way a
         * user learns their lookups have stopped being private.
         */
        val dnsFallbackActive: StateFlow<Boolean> =
            dnsPrivacyMonitor.usingSystemFallback
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

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

        init {
            refreshImageCacheUsage()
        }

        fun setActiveProvider(id: ProviderId) = update { repository.setActiveProviderId(id) }

        fun setPersonalizedHomeFeed(enabled: Boolean) = update { repository.setPersonalizedHomeFeed(enabled) }

        fun setHiddenTags(tags: String) = update { repository.setHiddenTags(tags) }

        fun setMutedTags(tags: String) = update { repository.setMutedTags(tags) }

        fun setHideNsfwBoards(enabled: Boolean) = update { repository.setHideNsfwBoards(enabled) }

        fun setHideTextOnlyThreads(enabled: Boolean) = update { repository.setHideTextOnlyThreads(enabled) }

        fun setHarshContentFilter(enabled: Boolean) = update { repository.setHarshContentFilter(enabled) }

        fun setDeepMediaScan(enabled: Boolean) = update { repository.setDeepMediaScan(enabled) }

        fun setMediaFilter(filter: MediaFilter) = update { repository.setMediaFilter(filter) }

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

        fun setFeedSort(sort: FeedSort) = update { repository.setFeedSort(sort) }

        fun setImageCacheLimitMb(megabytes: Int) = update { repository.setImageCacheLimitMb(megabytes) }

        fun refreshImageCacheUsage() =
            update {
                _imageCacheUsageBytes.value = imageCacheRepository.usageBytes()
            }

        fun clearImageCache() =
            update {
                imageCacheRepository.clear()
                _imageCacheUsageBytes.value = imageCacheRepository.usageBytes()
            }

        fun setUserAgent(userAgent: String) = update { repository.setUserAgent(userAgent) }

        fun setConnectTimeout(seconds: Long) = update { repository.setConnectTimeoutSeconds(seconds) }

        fun setReadTimeout(seconds: Long) = update { repository.setReadTimeoutSeconds(seconds) }

        fun setCertificateRevocationChecks(enabled: Boolean) = update { repository.setDisableOcspChecking(!enabled) }

        fun setDownloadFolderUri(uri: String) = update { repository.setDownloadFolderUri(uri) }

        fun setDownloadOrganization(organization: DownloadOrganization) =
            update { repository.setDownloadOrganization(organization) }

        fun setDohProvider(provider: DohProvider) = update { repository.setDohProvider(provider) }

        fun setBiometricLock(enabled: Boolean) = update { repository.setBiometricLockEnabled(enabled) }

        fun setSaveRecentSearches(enabled: Boolean) = update { repository.setSaveRecentSearches(enabled) }

        fun setInternalUpdater(enabled: Boolean) = update { repository.setInternalUpdaterEnabled(enabled) }

        fun setColorTheme(theme: ColorTheme) = update { repository.setColorTheme(theme) }

        fun setFullScreenFeedChrome(enabled: Boolean) = update { repository.setFullScreenFeedChrome(enabled) }

        fun setFeedRefreshInterval(interval: FeedRefreshInterval) =
            update { repository.setFeedRefreshInterval(interval) }

        fun setThreadPresentation(presentation: ThreadPresentation) =
            update { repository.setThreadPresentation(presentation) }

        fun setThreadWatchNotifications(enabled: Boolean) =
            update { repository.setThreadWatchNotificationsEnabled(enabled) }

        fun setQuietHoursStart(time: String) = update { repository.setQuietHoursStart(time) }

        fun setQuietHoursEnd(time: String) = update { repository.setQuietHoursEnd(time) }

        fun setMediaScrollThreadView(enabled: Boolean) = update { repository.setMediaScrollThreadView(enabled) }

        fun setMediaScrollBoardView(enabled: Boolean) = update { repository.setMediaScrollBoardView(enabled) }

        fun setAutoplayVideosInFeed(enabled: Boolean) = update { repository.setAutoplayVideosInFeed(enabled) }

        fun clearLocalActivity() =
            update {
                historyRepository.clear()
                searchRepository.clearRecentQueries()
                downloadRepository.clearHistory()
            }

        fun exportBackup(
            appVersionName: String,
            sink: suspend (String) -> Unit,
        ) = update {
            _backupStatus.value =
                runCatching { sink(backupService.exportToJson(appVersionName)) }
                    .fold(
                        onSuccess = { BackupStatus.Exported },
                        onFailure = { BackupStatus.Failed(it.message ?: "Could not write the backup file") },
                    )
        }

        fun importBackup(source: suspend () -> String) =
            update {
                _backupStatus.value =
                    runCatching { source() }
                        .mapCatching { backupService.importFromJson(it).getOrThrow() }
                        .fold(
                            onSuccess = { BackupStatus.Imported(it) },
                            onFailure = { BackupStatus.Failed(it.message ?: "That file is not a valid Orbin backup") },
                        )
            }

        fun checkForUpdate(currentVersionName: String) =
            update {
                _updateCheck.value = UpdateCheckState.Checking
                _updateCheck.value =
                    when (val result = updateRepository.checkForUpdate(currentVersionName)) {
                        is OrbinResult.Success -> UpdateCheckState.Result(result.data)
                        is OrbinResult.Failure -> UpdateCheckState.Failed(result.error.message)
                    }
            }

        fun exportDiagnostics(sink: suspend (String) -> Unit) =
            update {
                val report = diagnosticsRepository.exportReport()
                _diagnosticsStatus.value =
                    if (report == null) {
                        DiagnosticsStatus.Empty
                    } else {
                        runCatching { sink(report) }
                            .fold(
                                onSuccess = { DiagnosticsStatus.Exported },
                                onFailure = { DiagnosticsStatus.Failed(it.message ?: "Could not write the file") },
                            )
                    }
            }

        fun clearDiagnostics() =
            update {
                diagnosticsRepository.clearReports()
                _diagnosticsStatus.value = DiagnosticsStatus.Cleared
            }

        fun clearDiagnosticsStatus() {
            _diagnosticsStatus.value = null
        }

        fun clearBackupStatus() {
            _backupStatus.value = null
        }

        private fun update(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

private object EmptyImageCacheRepository : ImageCacheRepository {
    override suspend fun usageBytes(): Long = 0L

    override suspend fun clear() = Unit
}

sealed interface DiagnosticsStatus {
    data object Exported : DiagnosticsStatus

    data object Empty : DiagnosticsStatus

    data object Cleared : DiagnosticsStatus

    data class Failed(
        val message: String,
    ) : DiagnosticsStatus
}

sealed interface BackupStatus {
    data object Exported : BackupStatus

    data class Imported(
        val summary: BackupSummary,
    ) : BackupStatus

    data class Failed(
        val message: String,
    ) : BackupStatus
}

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState

    data object Checking : UpdateCheckState

    data class Result(
        val status: UpdateStatus,
    ) : UpdateCheckState

    data class Failed(
        val message: String,
    ) : UpdateCheckState
}
