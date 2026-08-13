package com.orbin.feature.settings

import com.orbin.core.model.AppSettings
import com.orbin.core.testing.repository.FakeBoardPreferencesRepository
import com.orbin.core.testing.repository.FakeBookmarkRepository
import com.orbin.core.testing.repository.FakeDiagnosticsRepository
import com.orbin.core.testing.repository.FakeDnsPrivacyMonitor
import com.orbin.core.testing.repository.FakeDownloadRepository
import com.orbin.core.testing.repository.FakeHistoryRepository
import com.orbin.core.testing.repository.FakeProviderRegistry
import com.orbin.core.testing.repository.FakeSearchRepository
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.core.testing.repository.FakeUpdateRepository

/**
 * Builds a real [SettingsViewModel] over in-memory repositories, shared by every settings
 * sub-screen's instrumentation tests. Every sub-screen takes the same ViewModel type, so the
 * subject stays each screen and its wiring to it rather than Hilt or the ViewModel's own logic.
 */
internal fun testSettingsViewModel(
    settingsRepository: FakeSettingsRepository,
    dnsMonitor: FakeDnsPrivacyMonitor = FakeDnsPrivacyMonitor(),
): SettingsViewModel =
    SettingsViewModel(
        repository = settingsRepository,
        historyRepository = FakeHistoryRepository(),
        searchRepository = FakeSearchRepository(),
        downloadRepository = FakeDownloadRepository(),
        backupService =
            BackupService(
                settingsRepository,
                FakeBoardPreferencesRepository(),
                FakeBookmarkRepository(),
                FakeSearchRepository(),
                FakeProviderRegistry(),
            ),
        updateRepository = FakeUpdateRepository(),
        diagnosticsRepository = FakeDiagnosticsRepository(),
        dnsPrivacyMonitor = dnsMonitor,
        registry = FakeProviderRegistry(),
    )

internal fun fakeSettingsRepository(initial: AppSettings = AppSettings.Default): FakeSettingsRepository =
    FakeSettingsRepository(initial)
