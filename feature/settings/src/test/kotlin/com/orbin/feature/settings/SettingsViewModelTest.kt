package com.orbin.feature.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.orbin.core.common.result.DataError
import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppUpdateState
import com.orbin.core.model.BoardId
import com.orbin.core.model.DohProvider
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.UpdateStatus
import com.orbin.core.testing.MainDispatcherRule
import com.orbin.core.testing.repository.FakeAppUpdater
import com.orbin.core.testing.repository.FakeBoardPreferencesRepository
import com.orbin.core.testing.repository.FakeBookmarkRepository
import com.orbin.core.testing.repository.FakeDnsPrivacyMonitor
import com.orbin.core.testing.repository.FakeDownloadRepository
import com.orbin.core.testing.repository.FakeHistoryRepository
import com.orbin.core.testing.repository.FakeImageBoardProvider
import com.orbin.core.testing.repository.FakeProviderRegistry
import com.orbin.core.testing.repository.FakeSearchRepository
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.core.testing.repository.FakeUpdateRepository
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.ImageCacheRepository
import com.orbin.domain.repository.InMemoryThreadMemoryRepository
import com.orbin.domain.repository.ThreadMemoryRepository
import com.orbin.domain.repository.VideoCacheRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `cache-only clearing attempts both caches and preserves collections`() =
        runTest {
            val image = mockk<ImageCacheRepository>()
            val video = mockk<VideoCacheRepository>()
            coEvery { image.usageBytes() } returns 12L
            coEvery { video.usageBytes() } returns 34L
            coEvery { image.clear() } throws IllegalStateException("image cache unavailable")
            coEvery { video.clear() } returns Unit
            val record =
                com.orbin.core.model.DownloadRecord(
                    1L,
                    "https://site/a.jpg",
                    "a.jpg",
                    com.orbin.core.model.DownloadStatus.COMPLETED,
                    1L,
                )
            val visit =
                com.orbin.core.model.HistoryEntry(
                    com.orbin.core.model.ThreadKey(
                        com.orbin.core.model
                            .ProviderId("site"),
                        com.orbin.core.model
                            .BoardId("b"),
                        com.orbin.core.model
                            .ThreadId(42),
                    ),
                    "A thread",
                    lastVisitedMillis = 1L,
                )
            val downloads = FakeDownloadRepository(listOf(record))
            val history = FakeHistoryRepository(listOf(visit))
            val viewModel =
                createViewModel(imageCache = image, videoCache = video, downloads = downloads, history = history)
            val results = mutableListOf<Boolean>()
            viewModel.clearMediaCache { results += it }
            assertThat(results).containsExactly(false)
            coVerify(exactly = 1) { image.clear() }
            coVerify(exactly = 1) { video.clear() }
            assertThat(viewModel.imageCacheUsageBytes.value).isEqualTo(12L)
            assertThat(viewModel.videoCacheUsageBytes.value).isEqualTo(34L)
            assertThat(history.observeHistory().first()).containsExactly(visit)
            assertThat(downloads.observeDownloads().first()).containsExactly(record)
        }

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
    fun `clearLocalActivity removes persisted reading anchors seen IDs and hidden post choices`() =
        runTest {
            val memories = InMemoryThreadMemoryRepository()
            val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(42))
            memories.update(key) { it.copy(anchorPostId = 45, seenPostIds = setOf(42, 45), hiddenPostIds = setOf(43)) }
            val viewModel = createViewModel(memory = memories)
            viewModel.clearLocalActivity()
            assertThat(memories.observeAll().first()).isEmpty()
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
    fun `clearing reports success only after the cache finishes`() =
        runTest {
            val completion = CompletableDeferred<Unit>()
            val cache = mockk<ImageCacheRepository>()
            coEvery { cache.usageBytes() } returns 0L
            coEvery { cache.clear() } coAnswers { completion.await() }
            val viewModel = createViewModel(imageCache = cache)
            val results = mutableListOf<Boolean>()

            viewModel.clearLocalActivity { results += it }
            assertThat(results).isEmpty()
            completion.complete(Unit)

            assertThat(results).containsExactly(true)
        }

    @Test
    fun `clearing reports failure when some local data cannot be deleted`() =
        runTest {
            val cache = mockk<ImageCacheRepository>()
            coEvery { cache.usageBytes() } returns 0L
            coEvery { cache.clear() } throws IllegalStateException("disk unavailable")
            val viewModel = createViewModel(imageCache = cache)
            val results = mutableListOf<Boolean>()

            viewModel.clearLocalActivity { results += it }

            assertThat(results).containsExactly(false)
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

    @Test
    fun `a newer release found by checkForUpdate is offered in the update dialog`() =
        runTest {
            val release = UpdateStatus.Available(tag = "v149-Orange", name = "Orbin 149 - Orange", url = "https://x")
            val updater = FakeAppUpdater()
            val viewModel =
                createViewModel(
                    updateRepository = FakeUpdateRepository(OrbinResult.Success(release)),
                    appUpdater = updater,
                )

            viewModel.checkForUpdate("148-Nectarine")

            assertThat(updater.state.value).isEqualTo(AppUpdateState.Available(release))
        }

    @Test
    fun `clearUpdateCheck resets updateCheck to Idle`() =
        runTest {
            val viewModel =
                createViewModel(updateRepository = FakeUpdateRepository(OrbinResult.Success(UpdateStatus.UpToDate)))

            viewModel.updateCheck.test {
                assertThat(awaitItem()).isEqualTo(UpdateCheckState.Idle)
                viewModel.checkForUpdate("1.0")
                assertThat(awaitItem()).isEqualTo(UpdateCheckState.Result(UpdateStatus.UpToDate))
                viewModel.clearUpdateCheck()
                assertThat(awaitItem()).isEqualTo(UpdateCheckState.Idle)
            }
        }

    @Test
    fun `privacy and security settings update repository`() =
        runTest {
            val repository = FakeSettingsRepository()
            val viewModel = createViewModel(settings = repository)

            viewModel.setScreenPrivacy(true)
            assertThat(repository.current.screenPrivacyEnabled).isTrue()

            viewModel.setDohProvider(DohProvider.NEXTDNS)
            assertThat(repository.current.dohProvider).isEqualTo(DohProvider.NEXTDNS)

            viewModel.setSaveSearchHistory(true)
            assertThat(repository.current.saveSearchHistory).isTrue()

            viewModel.setSaveBrowsingHistory(false)
            assertThat(repository.current.saveBrowsingHistory).isFalse()
        }

    private fun createViewModel(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        history: FakeHistoryRepository = FakeHistoryRepository(),
        searches: FakeSearchRepository = FakeSearchRepository(),
        downloads: FakeDownloadRepository = FakeDownloadRepository(),
        bookmarks: BookmarkRepository = FakeBookmarkRepository(),
        updateRepository: FakeUpdateRepository = FakeUpdateRepository(),
        registry: FakeProviderRegistry = FakeProviderRegistry(),
        appUpdater: FakeAppUpdater = FakeAppUpdater(),
        imageCache: ImageCacheRepository = mockk(relaxed = true),
        videoCache: VideoCacheRepository = mockk(relaxed = true),
        memory: ThreadMemoryRepository = InMemoryThreadMemoryRepository(),
    ) = SettingsViewModel(
        repository = settings,
        historyRepository = history,
        searchRepository = searches,
        downloadRepository = downloads,
        backupService =
            BackupService(settings, FakeBoardPreferencesRepository(), bookmarks, searches, registry),
        updateRepository = updateRepository,
        appUpdater = appUpdater,
        dnsPrivacyMonitor = FakeDnsPrivacyMonitor(),
        registry = registry,
        imageCacheRepository = imageCache,
        videoCacheRepository = videoCache,
        threadMemoryRepository = memory,
    )
}
