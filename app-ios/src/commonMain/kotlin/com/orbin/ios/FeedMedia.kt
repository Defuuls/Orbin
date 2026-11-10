package com.orbin.ios

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
import coil3.compose.AsyncImage
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.PlaybackSettings
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_media_open_in_browser
import com.orbin.ios.resources.ios_media_spoiler_reveal
import com.orbin.uinext.FeedPlaybackControls
import com.orbin.uinext.InlineAction
import com.orbin.uinext.rememberFeedPlaybackChoice
import org.jetbrains.compose.resources.stringResource

/** Only a settled page can fetch original media or own the native decoder. */
@Composable
internal fun FeedMedia(
    feed: FeedThread,
    playback: PlaybackSettings,
    active: Boolean,
    modifier: Modifier,
    preloader: NativeMediaPreloader?,
    onLike: (() -> Unit)? = null,
    onOpenExternal: (String) -> Unit,
) {
    val attachment =
        feed.thread.originalPost.attachments
            .firstOrNull() ?: return
    val identity = "${feed.feedRowId}/${attachment.id}/${attachment.sourceUrl}"
    var revealed by rememberFeedPlaybackChoice(identity, "revealed", !attachment.isSpoiler)
    if (!revealed) {
        // The cover is deliberately placed before even the thumbnail request.
        SpoilerCover(
            modifier = modifier.background(Color.Black).clickable(role = Role.Button) { revealed = true },
            text = stringResource(Res.string.ios_media_spoiler_reveal),
        )
        return
    }

    val source = remember(attachment) { safeExternalLink(attachment.sourceUrl) }
    val playable = source != null && (attachment.playsInApp || attachment.isWebM && supportsWebM)
    val autoplay = playback.autoplay && playable && attachment.type == MediaType.VIDEO
    var started by rememberFeedPlaybackChoice(identity, "started", autoplay)
    var initialMuted by rememberFeedPlaybackChoice(identity, "muted", playback.startMuted)
    var requestedPlaying by rememberFeedPlaybackChoice(identity, "playing", autoplay)

    Box(modifier.background(Color.Black)) {
        // A neighbour never requests a first video frame or creates a hidden decoder.
        FeedPoster(attachment, active, playback.autoplay, Modifier.fillMaxSize())
        when {
            active && playable && started -> {
                if (attachment.isWebM) {
                    NativeWebMPlayer(
                        url = requireNotNull(source),
                        active = true,
                        autoPlay = requestedPlaying,
                        initiallyMuted = initialMuted,
                        loop = playback.loopVideos,
                        feedMode = true,
                        preloader = preloader,
                        onFeedLike = onLike,
                        onPlayIntentChange = { requestedPlaying = it },
                        onMuteIntentChange = { initialMuted = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    NativePlayer(
                        url = requireNotNull(source),
                        active = true,
                        autoPlay = requestedPlaying,
                        initiallyMuted = initialMuted,
                        loop = playback.loopVideos,
                        feedMode = true,
                        preloader = preloader,
                        onFeedLike = onLike,
                        onPlayIntentChange = { requestedPlaying = it },
                        onMuteIntentChange = { initialMuted = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            active && playable ->
                FeedPlaybackControls(
                    playing = false,
                    muted = initialMuted,
                    onPlayPause = {
                        requestedPlaying = !requestedPlaying
                        started = true
                    },
                    // Changing the next start's audio choice must not fetch or start the clip.
                    onMuteToggle = { initialMuted = !initialMuted },
                    onDoubleTap = onLike,
                    modifier = Modifier.fillMaxSize(),
                )
            active && attachment.isPlayable && source != null ->
                InlineAction(
                    label = stringResource(Res.string.ios_media_open_in_browser),
                    onClick = { onOpenExternal(source) },
                )
        }
    }
}

@Composable
private fun FeedPoster(
    attachment: MediaAttachment,
    active: Boolean,
    autoplay: Boolean,
    modifier: Modifier,
) {
    AsyncImage(
        model = attachment.thumbnailUrl,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
    attachment.feedOriginalImageUrl(active, autoplay)?.let { source ->
        AsyncImage(
            model = source,
            contentDescription = attachment.originalFileName,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    }
}

/** A GIF's original may animate as soon as decoded; autoplay-off keeps its static poster. */
internal fun MediaAttachment.feedOriginalImageUrl(
    active: Boolean,
    autoplay: Boolean,
): String? {
    val originalAllowed = type == MediaType.IMAGE || (type == MediaType.ANIMATED_IMAGE && autoplay)
    return if (active && originalAllowed) safeExternalLink(sourceUrl) else null
}
