package com.orbin.media.video

/**
 * Remembers a reader's play/pause choice independently of buffering and app visibility.
 * Lifecycle pauses and page changes suspend playback without overwriting that choice.
 */
internal class PlaybackSession(
    autoplay: Boolean,
    private var foreground: Boolean = true,
) {
    var requestedPlaying: Boolean = autoplay
        private set

    fun toggle(ended: Boolean = false) {
        requestedPlaying = ended || !requestedPlaying
    }

    fun setForeground(visible: Boolean) {
        foreground = visible
    }

    fun shouldPlay(
        active: Boolean,
        hasError: Boolean,
    ): Boolean = requestedPlaying && foreground && active && !hasError
}
