package com.orbin.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.orbin.core.model.MediaType
import com.orbin.core.model.PlaybackPreferences
import com.orbin.media.image.MediaThumbnail
import com.orbin.media.image.SpoilerOverlay
import com.orbin.media.video.VideoPlayer
import com.orbin.media.video.canAutoplayInFeed
import com.orbin.uinext.FeedPlaybackControls
import com.orbin.uinext.rememberFeedPlaybackChoice

/** Only the settled page owns a decoder; its explicit play choice survives paging away. */
@Composable
internal fun FeedMedia(
    entry: FeedEntry,
    preferences: PlaybackPreferences,
    active: Boolean,
    modifier: Modifier = Modifier,
    onLike: (() -> Unit)? = null,
) {
    val attachment = entry.attachment ?: return
    val playback = remember(preferences, entry.key) { preferences.resolve(entry.key.provider, entry.key.board) }
    val identity = "${entry.row.id}/${attachment.id}/${attachment.sourceUrl}"
    var revealed by rememberFeedPlaybackChoice(identity, "revealed", !attachment.isSpoiler)

    if (!revealed) {
        // No original or thumbnail request is started until the reader explicitly reveals it.
        Box(
            modifier
                .background(Color.Black)
                .clickable(role = Role.Button) { revealed = true },
        ) {
            SpoilerOverlay(Modifier.fillMaxSize())
        }
        return
    }

    val shown = remember(attachment) { attachment.copy(isSpoiler = false) }
    val autoplay = playback.autoplay && canAutoplayInFeed(shown, autoplayEnabled = true)
    var started by rememberFeedPlaybackChoice(identity, "started", autoplay)
    var requestedPlaying by rememberFeedPlaybackChoice(identity, "playing", autoplay)
    var initialMuted by rememberFeedPlaybackChoice(identity, "muted", playback.startMuted)
    val playable = shown.type in setOf(MediaType.VIDEO, MediaType.AUDIO) && shown.sourceUrl.isNotBlank()

    if (active && playable && started) {
        VideoPlayer(
            url = shown.sourceUrl,
            autoPlay = requestedPlaying,
            muted = initialMuted,
            loop = playback.loopVideos,
            active = true,
            feedMode = true,
            onFeedLike = onLike,
            onPlayIntentChange = { requestedPlaying = it },
            onMuteIntentChange = { initialMuted = it },
            modifier = modifier,
        )
    } else {
        Box(modifier) {
            MediaThumbnail(
                attachment = shown,
                fullResolution = active,
                contentScale = ContentScale.Fit,
                playing = active && playback.autoplay && shown.type == MediaType.ANIMATED_IMAGE,
                showPlayBadge = false,
                cornerRadius = 0.dp,
                modifier = Modifier.fillMaxSize(),
            )
            if (active && playable) {
                FeedPlaybackControls(
                    playing = false,
                    muted = initialMuted,
                    onPlayPause = {
                        requestedPlaying = !requestedPlaying
                        started = true
                    },
                    onMuteToggle = { initialMuted = !initialMuted },
                    onDoubleTap = onLike,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
