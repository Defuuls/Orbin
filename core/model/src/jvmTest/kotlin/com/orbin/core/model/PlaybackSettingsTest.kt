package com.orbin.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackSettingsTest {
    @Test
    fun `boards inherit live app and site changes until explicitly overridden`() {
        val board = PlaybackScope("4chan", "gif")
        val app = PlaybackSettings(autoplay = false)
        val site = PlaybackSettings(startMuted = false)
        val overridden = PlaybackSettings(loopVideos = false)
        val preferences =
            PlaybackPreferences()
                .withSettings(PlaybackScope(), app)
                .withSettings(PlaybackScope("4chan"), site)
                .withSettings(board, overridden)
        assertThat(preferences.forScope(board)).isEqualTo(overridden)
        assertThat(preferences.forScope(PlaybackScope("4chan", "g"))).isEqualTo(site)
        assertThat(preferences.forScope(PlaybackScope("bbwchan", "gif"))).isEqualTo(app)
        assertThat(preferences.withSettings(board, null).forScope(board)).isEqualTo(site)
        assertThat(preferences.withSettings(board, null).withSettings(PlaybackScope("4chan"), null).forScope(board))
            .isEqualTo(app)
    }

    @Test
    fun `resetting a site preserves explicit board choices and sibling sites`() {
        val board = PlaybackScope("4chan", "g")
        val preferences =
            PlaybackPreferences()
                .withSettings(PlaybackScope("4chan"), PlaybackSettings(autoplay = false))
                .withSettings(board, PlaybackSettings(startMuted = false))
                .withSettings(PlaybackScope("bbwchan"), PlaybackSettings(loopVideos = false))
                .withSettings(PlaybackScope("4chan"), null)
        assertThat(preferences.forScope(board).startMuted).isFalse()
        assertThat(preferences.forScope(PlaybackScope("4chan", "gif"))).isEqualTo(PlaybackSettings())
        assertThat(preferences.forScope(PlaybackScope("bbwchan", "g")).loopVideos).isFalse()
    }
}
