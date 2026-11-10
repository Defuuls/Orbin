package com.orbin.media.video

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackSessionTest {
    @Test
    fun `a play tap restarts a finished clip instead of merely clearing its old request`() {
        val session = PlaybackSession(autoplay = true)
        session.toggle(ended = true)
        assertThat(session.shouldPlay(active = true, hasError = false)).isTrue()
    }

    @Test
    fun `manual pause survives background and foreground with autoplay enabled`() {
        val session = PlaybackSession(autoplay = true)
        session.toggle()
        session.setForeground(false)
        session.setForeground(true)
        assertThat(session.shouldPlay(active = true, hasError = false)).isFalse()
    }

    @Test
    fun `manual play resumes after background with autoplay disabled`() {
        val session = PlaybackSession(autoplay = false)
        session.toggle()
        assertThat(session.shouldPlay(active = true, hasError = false)).isTrue()
        session.setForeground(false)
        assertThat(session.shouldPlay(active = true, hasError = false)).isFalse()
        session.setForeground(true)
        assertThat(session.shouldPlay(active = true, hasError = false)).isTrue()
    }

    @Test
    fun `buffering does not prevent pausing a requested playback`() {
        val session = PlaybackSession(autoplay = true)
        // A decoder may not yet report isPlaying while the reader already asked it to play.
        session.toggle()
        assertThat(session.requestedPlaying).isFalse()
        assertThat(session.shouldPlay(active = true, hasError = false)).isFalse()
    }

    @Test
    fun `page visibility and errors suppress playback without discarding user intent`() {
        val session = PlaybackSession(autoplay = true)
        assertThat(session.shouldPlay(active = false, hasError = false)).isFalse()
        assertThat(session.shouldPlay(active = true, hasError = true)).isFalse()
        assertThat(session.shouldPlay(active = true, hasError = false)).isTrue()
    }
}
