package com.orbin.minimal

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.AppSettings
import com.orbin.core.model.FeedRefreshInterval
import com.orbin.core.model.FeedSort
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.MediaFilter
import com.orbin.core.model.PreloadOption
import com.orbin.core.model.PreloadThrottleMode
import com.orbin.core.model.ThreadPresentation
import com.orbin.core.testing.repository.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MinimalExperiencePolicyTest {
    @Test
    fun `profile overrides settings that shared screens can observe`() =
        runTest {
            val repository =
                FakeSettingsRepository(
                    AppSettings.Default.copy(
                        hideNsfwBoards = true,
                        hideTextOnlyThreads = true,
                        harshContentFilter = true,
                        mediaFilter = MediaFilter.VIDEOS,
                        feedRefreshInterval = FeedRefreshInterval.NEVER,
                        feedThreadLimit = FeedThreadLimit.ALL,
                        feedSort = FeedSort.ACTIVITY,
                        threadPresentation = ThreadPresentation.OVERLAY,
                        autoplayVideos = true,
                        autoplayVideosInFeed = true,
                        muteByDefault = false,
                        preloadImages = false,
                        preloadOption = PreloadOption.NONE,
                        preloadThrottleMode = PreloadThrottleMode.UNLIMITED,
                        threadWatchNotificationsEnabled = true,
                        internalUpdaterEnabled = true,
                    ),
                )

            applyMinimalExperienceProfile(repository)
            val settings = repository.settings.first()

            assertThat(settings.hideNsfwBoards).isFalse()
            assertThat(settings.hideTextOnlyThreads).isFalse()
            assertThat(settings.harshContentFilter).isFalse()
            assertThat(settings.mediaFilter).isEqualTo(MediaFilter.ALL)
            assertThat(settings.feedRefreshInterval).isEqualTo(FeedRefreshInterval.FIVE_MINUTES)
            assertThat(settings.feedThreadLimit).isEqualTo(FeedThreadLimit.TWELVE)
            assertThat(settings.feedSort).isEqualTo(FeedSort.BOARD)
            assertThat(settings.threadPresentation).isEqualTo(ThreadPresentation.PAGE)
            assertThat(settings.autoplayVideos).isFalse()
            assertThat(settings.autoplayVideosInFeed).isFalse()
            assertThat(settings.muteByDefault).isTrue()
            assertThat(settings.preloadImages).isTrue()
            assertThat(settings.preloadOption).isEqualTo(PreloadOption.IMAGES)
            assertThat(settings.preloadThrottleMode).isEqualTo(PreloadThrottleMode.MODERATE)
            assertThat(settings.threadWatchNotificationsEnabled).isFalse()
            assertThat(settings.internalUpdaterEnabled).isFalse()
        }
}
