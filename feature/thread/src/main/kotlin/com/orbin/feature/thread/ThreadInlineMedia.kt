package com.orbin.feature.thread

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.PlaybackSettings
import com.orbin.media.image.MediaThumbnail
import com.orbin.media.video.VideoPlayer
import com.orbin.media.video.prepareVideoPlaybackHandoff
import com.orbin.uinext.components.FeedPlaybackControls
import com.orbin.uinext.components.ThreadInlineFullscreenButton
import com.orbin.uinext.components.ThreadInlineMediaFrame
import com.orbin.uinext.components.ThreadInlineMediaState

@Composable
internal fun ThreadInlineMedia(
    attachment: MediaAttachment,
    identity: String,
    state: ThreadInlineMediaState,
    playback: PlaybackSettings,
    onOpenFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playable = attachment.type == MediaType.VIDEO && attachment.sourceUrl.startsWith("https://")
    if (!playable) {
        MediaThumbnail(
            attachment,
            modifier = modifier,
            fullResolution = false,
            cornerRadius = 0.dp,
            onClick = onOpenFullscreen,
        )
        return
    }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsState()
    var started by rememberSaveable(identity, playback.autoplay) { mutableStateOf(playback.autoplay) }
    var requested by rememberSaveable(identity, playback.autoplay) { mutableStateOf(playback.autoplay) }
    var muted by rememberSaveable(identity, playback.startMuted) { mutableStateOf(playback.startMuted) }
    val open = {
        prepareVideoPlaybackHandoff(attachment.sourceUrl)
        onOpenFullscreen()
    }
    ThreadInlineMediaFrame(state, identity, lifecycle.isAtLeast(Lifecycle.State.RESUMED), modifier) { active ->
        MediaThumbnail(
            attachment,
            modifier = Modifier.fillMaxSize(),
            fullResolution = false,
            cornerRadius = 0.dp,
            onClick = open,
        )
        if (active && started) {
            VideoPlayer(
                attachment.sourceUrl,
                Modifier.fillMaxSize(),
                autoPlay = requested,
                muted = muted,
                active = true,
                loop = playback.loopVideos,
                feedMode = true,
                onPlayIntentChange = { requested = it },
                onMuteIntentChange = { muted = it },
            )
        } else {
            FeedPlaybackControls(
                playing = false,
                muted = muted,
                onPlayPause = {
                    state.requestPlay(identity)
                    started = true
                    requested = true
                },
                onMuteToggle = { muted = !muted },
                modifier = Modifier.fillMaxSize(),
            )
        }
        ThreadInlineFullscreenButton(identity, open, Modifier.align(Alignment.TopEnd).padding(4.dp))
    }
}
