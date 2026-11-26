package com.orbin.ios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import com.orbin.uinext.components.FeedPlaybackControls
import com.orbin.uinext.components.ThreadInlineFullscreenButton
import com.orbin.uinext.components.ThreadInlineMediaFrame
import com.orbin.uinext.components.ThreadInlineMediaState

/** The parent spoiler cover and thread projection must approve a post before composing this slot. */
@Composable
internal fun ThreadInlineMedia(
    attachment: MediaAttachment,
    identity: String,
    state: ThreadInlineMediaState,
    playback: PlaybackSettings,
    onOpenFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    players: InlineNativePlayers? = null,
) {
    val source = safeExternalLink(attachment.sourceUrl)
    val playable =
        attachment.type == MediaType.VIDEO &&
            source != null &&
            (attachment.playsInApp || attachment.isWebM && supportsWebM)
    if (!playable) {
        Box(modifier.clickable(onClick = onOpenFullscreen)) {
            ReaderThumbnail(attachment, Modifier.fillMaxSize(), attachment.originalFileName)
        }
        return
    }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsState()
    var started by rememberSaveable(identity, playback.autoplay) { mutableStateOf(playback.autoplay) }
    var requested by rememberSaveable(identity, playback.autoplay) { mutableStateOf(playback.autoplay) }
    var muted by rememberSaveable(identity, playback.startMuted) { mutableStateOf(playback.startMuted) }
    val open = {
        prepareNativeMediaHandoff(requireNotNull(source))
        onOpenFullscreen()
    }
    ThreadInlineMediaFrame(state, identity, lifecycle.isAtLeast(Lifecycle.State.RESUMED), modifier) { active ->
        ReaderThumbnail(attachment, Modifier.fillMaxSize(), attachment.originalFileName)
        if (active && started) {
            SystemPlayable(requireNotNull(source), showStatus = false) { playableUrl ->
                if (attachment.isWebM) {
                    NativeWebMPlayer(
                        playableUrl,
                        Modifier.fillMaxSize(),
                        active = true,
                        autoPlay = requested,
                        initiallyMuted = muted,
                        loop = playback.loopVideos,
                        feedMode = true,
                        onPlayIntentChange = { requested = it },
                        onMuteIntentChange = { muted = it },
                    )
                } else {
                    ProvideInlineNativePlayers(players) {
                        NativePlayer(
                            playableUrl,
                            active = true,
                            loop = playback.loopVideos,
                            Modifier.fillMaxSize(),
                            autoPlay = requested,
                            initiallyMuted = muted,
                            feedMode = true,
                            onPlayIntentChange = { requested = it },
                            onMuteIntentChange = { muted = it },
                        )
                    }
                }
            }
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
