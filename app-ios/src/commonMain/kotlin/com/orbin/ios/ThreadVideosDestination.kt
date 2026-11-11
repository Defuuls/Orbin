package com.orbin.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.Thread
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_downloads_retry
import com.orbin.ios.resources.ios_media_close
import com.orbin.ios.resources.ios_thread_videos_empty
import com.orbin.ios.resources.ios_thread_videos_loading
import com.orbin.ios.resources.ios_thread_videos_title
import com.orbin.uinext.InlineAction
import com.orbin.uinext.NextCircularProgress
import com.orbin.uinext.NextIconAction
import com.orbin.uinext.NextTheme
import com.orbin.uinext.next
import com.orbin.uinext.tokens.NextType
import org.jetbrains.compose.resources.stringResource

/** The feed's chat action opens videos from every post, without first showing comments. */
@Composable
internal fun ThreadVideosDestination(
    browser: Browser,
    downloads: MediaDownloads,
    route: Route.ThreadVideos,
) {
    val load by browser.thread.collectAsState()
    val settings by browser.settings.current.collectAsState()
    val close = {
        browser.back()
        Unit
    }
    when (val state = load) {
        Load.Loading ->
            ThreadVideosStatus(
                message = stringResource(Res.string.ios_thread_videos_loading),
                loading = true,
                onClose = close,
            )
        is Load.Failed -> ThreadVideosStatus(message = state.message, onRetry = browser::retry, onClose = close)
        is Load.Ready -> {
            val thread = state.value.takeIf { it.key == route.key }
            val videos = remember(thread) { thread?.videos.orEmpty() }
            when {
                thread == null ->
                    ThreadVideosStatus(
                        message = stringResource(Res.string.ios_thread_videos_loading),
                        loading = true,
                        onClose = close,
                    )
                videos.isEmpty() ->
                    ThreadVideosStatus(message = stringResource(Res.string.ios_thread_videos_empty), onClose = close)
                else ->
                    MediaViewer(
                        files = videos,
                        startIndex = 0,
                        onClose = close,
                        onSave = { file -> downloads.save(file, thread.key, thread.subject) },
                        playback = settings.playbackPreferences.resolve(thread.key.provider, thread.key.board),
                    )
            }
        }
    }
}

/** Natural post/file order is retained; filtering never remaps a video to an image attachment. */
internal val Thread.videos: List<MediaAttachment>
    get() = files.filter { it.type == MediaType.VIDEO }

/** Every non-playing state keeps an explicit exit and never redirects the reader to comments. */
@Composable
private fun ThreadVideosStatus(
    message: String,
    onClose: () -> Unit,
    loading: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    val title = stringResource(Res.string.ios_thread_videos_title)
    NextTheme(darkTheme = true, amoled = true) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingPadding()
                .semantics { paneTitle = title },
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    title,
                    style = NextType.titleMedium,
                    color = Color.White,
                    modifier = Modifier.semantics { heading() },
                )
                if (loading) NextCircularProgress()
                Text(message, style = NextType.body, color = next.muted, textAlign = TextAlign.Center)
                if (onRetry != null) {
                    InlineAction(stringResource(Res.string.ios_downloads_retry), accent = true, onClick = onRetry)
                }
            }
            NextIconAction(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(Res.string.ios_media_close),
                onClick = onClose,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 8.dp),
            )
        }
    }
}
