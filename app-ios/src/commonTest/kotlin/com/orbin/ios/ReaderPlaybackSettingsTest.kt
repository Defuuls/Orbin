package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.PlaybackPreferences
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.PlaybackSettings
import com.orbin.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReaderPlaybackSettingsTest {
    @Test
    fun playbackWaitsForStoredPreferencesThenLoadsTheBoardOverride() =
        runTest {
            val stored = MutableSharedFlow<AppSettings>(replay = 1)
            val repository =
                object : SettingsRepository by FakeSettings() {
                    override val settings: Flow<AppSettings> = stored
                }
            val reader = ReaderSettings(repository, FakeHistory(), backgroundScope)
            assertFalse(reader.current.value.playbackPreferences.defaults.autoplay)
            assertFalse(reader.current.value.playbackPreferences.defaults.preloadNextClip)

            val board = PlaybackScope("site", "g")
            val playback = PlaybackSettings(autoplay = false, startMuted = false)
            stored.emit(AppSettings(playbackPreferences = PlaybackPreferences().withSettings(board, playback)))
            runCurrent()
            assertEquals(
                playback,
                reader.current.value.playbackPreferences
                    .forScope(board),
            )
        }
}
