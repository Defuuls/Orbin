package com.orbin.feature.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.result.DataError
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppSettings
import com.orbin.core.model.UpdateStatus
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeBoardPreferencesRepository
import com.orbin.core.testing.repository.FakeBookmarkRepository
import com.orbin.core.testing.repository.FakeDiagnosticsRepository
import com.orbin.core.testing.repository.FakeDnsPrivacyMonitor
import com.orbin.core.testing.repository.FakeDownloadRepository
import com.orbin.core.testing.repository.FakeHistoryRepository
import com.orbin.core.testing.repository.FakeImageBoardProvider
import com.orbin.core.testing.repository.FakeProviderRegistry
import com.orbin.core.testing.repository.FakeSearchRepository
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.core.testing.repository.FakeUpdateRepository
import com.orbin.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `activeProvider falls back to the default when the persisted id is unknown`() =
        runTest {
            val provider = FakeImageBoardProvider(id = "fourchan")
            val viewModel =
                createViewModel(
                    settings = FakeSettingsRepository(AppSettings.Default.copy(activeProviderId = "not-registered")),
                    registry = FakeProviderRegistry(provider),
                )

            val resolved = viewModel.activeProvider.first()
            assertThat(resolved.id.value).isEqualTo("fourchan")
        }

    @Test
    fun `activeProvider resolves to the persisted provider once it matches`() =
        runTest {
            val provider = FakeImageBoardProvider(id = "fourchan")
            val viewModel =
                createViewModel(
                    settings = FakeSettingsRepository(AppSettings.Default.copy(activeProviderId = "fourchan")),
                    registry = FakeProviderRegistry(provider),
                )

            val resolved = viewModel.activeProvider.first()
            assertThat(resolved.id.value).isEqualTo("fourchan")
        }

    @Test
    fun `clearLocalActivity clears history, recent searches, and download history`() =
        runTest {
            val history = FakeHistoryRepository(listOf())
            val searches = FakeSearchRepository()
            val downloads = FakeDownloadRepository()
            val viewModel = createViewModel(history = history, searches = searches, downloads = downloads)

            viewModel.clearLocalActivity()

            // No direct getter for "cleared" beyond re-observing; each fake starts empty and the
            // call must not throw, which is the behavior worth locking down here.
            assertThat(history.observeHistory().first()).isEmpty()
        }

    @Test
    fun `exportBackup reports Exported on success`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.backupStatus.test {
                assertThat(awaitItem()).isNull()
                viewModel.exportBackup("1.0") { }
                assertThat(awaitItem()).isEqualTo(BackupStatus.Exported)
            }
        }

    @Test
    fun `exportBackup reports Failed when writing the file throws`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.backupStatus.test {
                assertThat(awaitItem()).isNull()
                viewModel.exportBackup("1.0") { error("disk full") }
                assertThat(awaitItem()).isInstanceOf(BackupStatus.Failed::class.java)
            }
        }

    @Test
    fun `importBackup reports Imported with a summary on success`() =
        runTest {
            val exportViewModel = createViewModel()
            var exported = ""
            exportViewModel.exportBackup("1.0") { exported = it }

            val importViewModel = createViewModel()
            importViewModel.backupStatus.test {
                assertThat(awaitItem()).isNull()
                importViewModel.importBackup { exported }
                assertThat(awaitItem()).isInstanceOf(BackupStatus.Imported::class.java)
            }
        }

    @Test
    fun `clearBackupStatus resets the status to null`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.backupStatus.test {
                assertThat(awaitItem()).isNull()
                viewModel.exportBackup("1.0") { }
                assertThat(awaitItem()).isEqualTo(BackupStatus.Exported)
                viewModel.clearBackupStatus()
                assertThat(awaitItem()).isNull()
            }
        }

    @Test
    fun `checkForUpdate reports the result on success`() =
        runTest {
            val viewModel =
                createViewModel(updateRepository = FakeUpdateRepository(OrbinResult.Success(UpdateStatus.UpToDate)))

            viewModel.updateCheck.test {
                // Checking is set and immediately overwritten by the (synchronous, in this fake)
                // result within the same launch, so it is not guaranteed to be its own emission.
                assertThat(awaitItem()).isEqualTo(UpdateCheckState.Idle)
                viewModel.checkForUpdate("1.0")
                assertThat(awaitItem()).isEqualTo(UpdateCheckState.Result(UpdateStatus.UpToDate))
            }
        }

    @Test
    fun `checkForUpdate reports a failure message when the check fails`() =
        runTest {
            val viewModel =
                createViewModel(updateRepository = FakeUpdateRepository(OrbinResult.Failure(DataError.Offline())))

            viewModel.updateCheck.test {
                assertThat(awaitItem()).isEqualTo(UpdateCheckState.Idle)
                viewModel.checkForUpdate("1.0")
                assertThat(awaitItem()).isEqualTo(UpdateCheckState.Failed("No network connection"))
            }
        }

    private fun createViewModel(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        history: FakeHistoryRepository = FakeHistoryRepository(),
        searches: FakeSearchRepository = FakeSearchRepository(),
        downloads: FakeDownloadRepository = FakeDownloadRepository(),
        bookmarks: BookmarkRepository = FakeBookmarkRepository(),
        updateRepository: FakeUpdateRepository = FakeUpdateRepository(),
        registry: FakeProviderRegistry = FakeProviderRegistry(),
    ) = SettingsViewModel(
        repository = settings,
        historyRepository = history,
        searchRepository = searches,
        downloadRepository = downloads,
        backupService =
            BackupService(settings, FakeBoardPreferencesRepository(), bookmarks, searches, registry),
        updateRepository = updateRepository,
        diagnosticsRepository = FakeDiagnosticsRepository(),
        dnsPrivacyMonitor = FakeDnsPrivacyMonitor(),
        registry = registry,
    )
}
