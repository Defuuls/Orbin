package com.orbin.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryImplTest {
    @Test
    fun `empty preferences read exactly as app defaults`() =
        runTest {
            val repository = repository()

            assertThat(repository.settings.first()).isEqualTo(AppSettings.Default)
        }

    @Test
    fun `every app setting setter persists and reads back`() =
        runTest {
            val repository = repository()

            repository.setPersonalizedHomeFeed(false)
            repository.setHiddenTags("hidden")
            repository.setMutedTags("muted")
            repository.setHideNsfwBoards(true)
            repository.setHideTextOnlyThreads(true)
            repository.setHarshContentFilter(true)
            repository.setDeepMediaScan(true)
            repository.setMediaFilter(MediaFilter.VIDEOS)
            repository.setFeedRefreshInterval(FeedRefreshInterval.FIFTEEN_MINUTES)
            repository.setThreadPresentation(ThreadPresentation.OVERLAY)
            repository.setThemeMode(AppThemeMode.DARK)
            repository.setColorTheme(ColorTheme.TOMORROW_NIGHT)
            repository.setDynamicColor(false)
            repository.setAmoled(true)
            repository.setFontScale(1.2f)
            repository.setFullScreenFeedChrome(true)
            repository.setThumbnailSize(ThumbnailSize.LARGE)
            repository.setAutoplayVideos(true)
            repository.setMuteByDefault(false)
            repository.setFullscreenVideoPlayback(true)
            repository.setAutoRotateVideoFullscreen(true)
            repository.setPreloadImages(false)
            repository.setPreloadOption(PreloadOption.ALL)
            repository.setPreloadThrottleMode(PreloadThrottleMode.AGGRESSIVE)
            repository.setFeedThreadLimit(FeedThreadLimit.ALL)
            repository.setFeedSort(FeedSort.TITLE)
            repository.setImageCacheLimitMb(512)
            repository.setDownloadFolderUri("content://downloads/tree/orbin")
            repository.setDownloadOrganization(DownloadOrganization.BY_THREAD)
            repository.setUserAgent("Orbin-Test/1.0")
            repository.setDohProvider(DohProvider.NEXTDNS)
            repository.setConnectTimeoutSeconds(60)
            repository.setReadTimeoutSeconds(120)
            repository.setDisableOcspChecking(false)
            repository.setBiometricLockEnabled(true)
            repository.setSaveRecentSearches(true)
            repository.setInternalUpdaterEnabled(false)
            repository.setThreadWatchNotificationsEnabled(false)
            repository.setQuietHoursStart("22:00")
            repository.setQuietHoursEnd("07:00")
            repository.setActiveProviderId(ProviderId("test-provider"))
            repository.setOnboardingCompleted(true)
            repository.setMediaScrollThreadView(false)
            repository.setMediaScrollBoardView(true)
            repository.setAutoplayVideosInFeed(true)

            val expected =
                AppSettings.Default.copy(
                    personalizedHomeFeed = false,
                    hiddenTags = "hidden",
                    mutedTags = "muted",
                    hideNsfwBoards = true,
                    hideTextOnlyThreads = true,
                    harshContentFilter = true,
                    deepMediaScan = true,
                    mediaFilter = MediaFilter.VIDEOS,
                    feedRefreshInterval = FeedRefreshInterval.FIFTEEN_MINUTES,
                    themeMode = AppThemeMode.DARK,
                    colorTheme = ColorTheme.TOMORROW_NIGHT,
                    dynamicColor = false,
                    amoled = true,
                    fontScale = 1.2f,
                    fullScreenFeedChrome = true,
                    threadPresentation = ThreadPresentation.OVERLAY,
                    thumbnailSize = ThumbnailSize.LARGE,
                    autoplayVideos = true,
                    muteByDefault = false,
                    fullscreenVideoPlayback = true,
                    autoRotateVideoFullscreen = true,
                    preloadImages = false,
                    preloadOption = PreloadOption.ALL,
                    preloadThrottleMode = PreloadThrottleMode.AGGRESSIVE,
                    imageCacheLimitMb = 512,
                    feedThreadLimit = FeedThreadLimit.ALL,
                    feedSort = FeedSort.TITLE,
                    downloadFolderUri = "content://downloads/tree/orbin",
                    downloadOrganization = DownloadOrganization.BY_THREAD,
                    userAgent = "Orbin-Test/1.0",
                    dohProvider = DohProvider.NEXTDNS,
                    connectTimeoutSeconds = 60,
                    readTimeoutSeconds = 120,
                    disableOcspChecking = false,
                    biometricLockEnabled = true,
                    saveRecentSearches = true,
                    internalUpdaterEnabled = false,
                    threadWatchNotificationsEnabled = false,
                    quietHoursStart = "22:00",
                    quietHoursEnd = "07:00",
                    activeProviderId = "test-provider",
                    onboardingCompleted = true,
                    mediaScrollThreadView = false,
                    mediaScrollBoardView = true,
                    autoplayVideosInFeed = true,
                )

            assertThat(repository.settings.first()).isEqualTo(expected)
        }

    private fun kotlinx.coroutines.test.TestScope.repository(): SettingsRepositoryImpl {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore =
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                context.preferencesDataStoreFile("settings-${UUID.randomUUID()}")
            }
        return SettingsRepositoryImpl(dataStore, backgroundScope)
    }
}
