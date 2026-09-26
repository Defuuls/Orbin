package com.orbin.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbin.core.common.network.DnsPrivacyMonitor
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.FormFactor
import com.orbin.core.model.ProviderId
import com.orbin.core.model.UpdateStatus
import com.orbin.domain.repository.AppUpdater
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
        private val appUpdater: AppUpdater,
        dnsPrivacyMonitor: DnsPrivacyMonitor,
        registry: ProviderRegistry,
        private val imageCacheRepository: ImageCacheRepository = EmptyImageCacheRepository,
    ) : ViewModel() {
        private val _backupStatus = MutableStateFlow<BackupStatus?>(null)
        private val _updateCheck = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
        private val _imageCacheUsageBytes = MutableStateFlow(0L)

        val imageCacheUsageBytes: StateFlow<Long> = _imageCacheUsageBytes.asStateFlow()

        /** Whether the app looks for a new release when it opens. */
        val checkUpdatesOnLaunch: StateFlow<Boolean> = appUpdater.checkOnLaunch

        fun setCheckUpdatesOnLaunch(enabled: Boolean) = appUpdater.setCheckOnLaunch(enabled)

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

        fun setHideNsfwBoards(enabled: Boolean) = update { repository.setHideNsfwBoards(enabled) }

        fun setDeepMediaScan(enabled: Boolean) = update { repository.setDeepMediaScan(enabled) }

        fun setFeedColumns(
            formFactor: FormFactor,
            columns: Int,
        ) = update { repository.setFeedColumns(formFactor, columns) }

        fun setThemeMode(mode: AppThemeMode) = update { repository.setThemeMode(mode) }

        fun setAmoled(enabled: Boolean) = update { repository.setAmoled(enabled) }

        fun refreshImageCacheUsage() =
            update {
                _imageCacheUsageBytes.value = imageCacheRepository.usageBytes()
            }

        fun clearImageCache() =
            update {
                imageCacheRepository.clear()
                _imageCacheUsageBytes.value = imageCacheRepository.usageBytes()
            }

        fun setBiometricLock(enabled: Boolean) = update { repository.setBiometricLockEnabled(enabled) }

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
                        is OrbinResult.Success -> {
                            // A newer release opens the same in-app update dialog the launch check does.
                            (result.data as? UpdateStatus.Available)?.let(appUpdater::offer)
                            UpdateCheckState.Result(result.data)
                        }
                        is OrbinResult.Failure -> UpdateCheckState.Failed(result.error.message)
                    }
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
