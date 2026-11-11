package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.PlaybackPreferences
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.PlaybackSettings
import com.orbin.domain.repository.SettingsRepository
import com.orbin.uinext.OFF_LABEL
import com.orbin.uinext.ON_LABEL
import com.orbin.uinext.SettingKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReaderPlaybackSettingsTest {
    @Test
    fun chatButtonPreferencesAreGlobalAndDefaultToThreadVideos() =
        runTest {
            val repository = FakeSettings()
            val reader = ReaderSettings(repository, FakeHistory(), backgroundScope)
            runCurrent()
            val board = PlaybackScope("site", "g")
            val scopes = listOf(PlaybackScope(), board)
            val before = repository.settings.value.playbackPreferences

            fun row(scope: PlaybackScope) =
                playbackRows(reader.current.value, emptyList(), scope)
                    .single { it.id == SettingIds.OPEN_THREAD_COMMENTS }

            scopes.forEach { scope ->
                assertEquals(SettingKind.TOGGLE, row(scope).kind)
                assertEquals(OFF_LABEL, row(scope).value)
                assertEquals(false, row(scope).checked)
            }

            reader.setOpenThreadComments(true).join()
            runCurrent()

            assertTrue(repository.settings.value.openThreadComments)
            scopes.forEach { scope ->
                assertEquals(ON_LABEL, row(scope).value)
                assertEquals(true, row(scope).checked)
            }
            assertEquals(before, repository.settings.value.playbackPreferences)

            reader.setOpenThreadComments(false).join()
            runCurrent()
            assertFalse(reader.current.value.openThreadComments)
        }

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

    @Test
    fun playbackChoicesChangeOnlyTheSelectedBoardAfterAnOptionIsChosen() =
        runTest {
            val repository = FakeSettings()
            val reader = ReaderSettings(repository, FakeHistory(), backgroundScope)
            runCurrent()
            val board = PlaybackScope("site", "gen")
            val before = repository.settings.value
            for (id in listOf("autoplay", "preloadNextClip")) {
                assertFalse(reader.activatePlayback(id, repository.settings.value, board))
                runCurrent()
                assertFalse(
                    repository.settings.value.playbackPreferences
                        .hasOverride(board),
                )
            }
            assertEquals(before, repository.settings.value)
            for (id in listOf("autoplay", "preloadNextClip")) {
                assertTrue(reader.selectPlaybackChoice(id, 0, repository.settings.value, board))
                runCurrent()
            }
            assertEquals(before.playbackPreferences.defaults, repository.settings.value.playbackPreferences.defaults)
            val effective =
                repository.settings.value.playbackPreferences
                    .forScope(board)
            assertFalse(effective.autoplay)
            assertFalse(effective.preloadNextClip)
            assertTrue(reader.selectPlaybackChoice("autoplay", 1, repository.settings.value, board))
            runCurrent()
            assertTrue(
                repository.settings.value.playbackPreferences
                    .forScope(board)
                    .autoplay,
            )
        }
}
