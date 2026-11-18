package com.orbin.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orbin.uinext.components.reader
import com.orbin.uinext.tokens.OrbinType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.math.roundToInt

/**
 * Playing a remote video under strict encrypted DNS.
 *
 * The system players cannot be given the remote address: they would look it up with the device's
 * DNS (see [SystemMediaPolicy]). So the app fetches the file itself, through its own client and
 * encrypted DNS, stages it in the same protected folder a saved file is played from, and hands the
 * player that local file. The copy is deleted as soon as the player leaves the screen.
 *
 * The cost is that playback starts when the whole file has arrived instead of while it loads, and
 * a file over the app's in-memory limit cannot be played this way and has to be saved instead.
 */
internal sealed interface StagedPlayback {
    /** [fraction] is 0..1, or null when the site does not say how large the file is. */
    data class Loading(
        val fraction: Float?,
    ) : StagedPlayback

    data class Ready(
        val fileUrl: String,
    ) : StagedPlayback

    data object Failed : StagedPlayback
}

/** Where the in-process fetch comes from; set once at startup beside the downloads' own fetch. */
internal object StagedStreaming {
    @Volatile
    var fetch: MediaFetch? = null

    /** Whole files are held in memory while they are written, so only a couple at a time. */
    val gate = Semaphore(MAX_CONCURRENT_STAGED)
}

/**
 * Fetches [url], stages it, reports each step, then holds the staged file until cancelled, when it
 * is released. Never returns normally after staging: the caller's scope ending is what ends it.
 */
internal suspend fun holdStagedMedia(
    url: String,
    fetch: MediaFetch,
    stage: suspend (bytes: ByteArray, fileName: String) -> String,
    release: suspend (path: String) -> Unit,
    fileUrl: (path: String) -> String,
    report: (StagedPlayback) -> Unit,
) {
    var staged: String? = null
    try {
        report(StagedPlayback.Loading(null))
        val bytes =
            fetch(url) { received, total ->
                report(
                    StagedPlayback.Loading(
                        total?.takeIf { it > 0 }?.let { (received.toFloat() / it).coerceIn(0f, 1f) },
                    ),
                )
            }
        check(bytes.isNotEmpty()) { "Empty media" }
        staged = stage(bytes, stagedMediaName(url.substringBefore('?').substringBefore('#').substringAfterLast('/')))
        report(StagedPlayback.Ready(fileUrl(staged)))
        awaitCancellation()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (
        @Suppress("TooGenericExceptionCaught") _: Exception,
    ) {
        // Too large for memory, offline, refused by the site: the reader can still save the file.
        report(StagedPlayback.Failed)
    } finally {
        withContext(NonCancellable) { staged?.let { release(it) } }
    }
}

/** A basename that cannot leave its staging folder, keeping the extension the player needs. */
internal fun stagedMediaName(raw: String): String =
    raw
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .filterNot(Char::isISOControl)
        .replace("..", "_")
        .ifBlank { "saved-media" }

/**
 * Gives [content] an address the system player may open: [url] itself when streaming is allowed
 * or it is already a local file, otherwise a staged local copy once it has been fetched.
 *
 * @param showStatus the full viewer shows progress and failure; a feed or inline card keeps its
 *   thumbnail until the file is ready.
 */
@Composable
internal fun SystemPlayable(
    url: String,
    showStatus: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (playableUrl: String) -> Unit,
) {
    if (systemLoadPermitted(url)) {
        content(url)
        return
    }
    val fetch = StagedStreaming.fetch
    var state by remember(url) { mutableStateOf<StagedPlayback>(StagedPlayback.Loading(null)) }
    LaunchedEffect(url, fetch) {
        if (fetch == null) {
            state = StagedPlayback.Failed
        } else {
            StagedStreaming.gate.withPermit {
                holdStagedMedia(
                    url,
                    fetch,
                    ::stageFetchedMedia,
                    ::releaseSavedMedia,
                    ::savedMediaFileUrl,
                ) { state = it }
            }
        }
    }
    when (val current = state) {
        is StagedPlayback.Ready -> content(current.fileUrl)
        is StagedPlayback.Loading -> if (showStatus) StagedStatus(stagedLoadingMessage(current.fraction), modifier)
        StagedPlayback.Failed -> if (showStatus) StagedStatus(STAGED_FAILED_MESSAGE, modifier)
    }
}

@Composable
private fun StagedStatus(
    message: String,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = OrbinType.body, color = reader.ink, textAlign = TextAlign.Center)
    }
}

internal fun stagedLoadingMessage(fraction: Float?): String =
    if (fraction == null) {
        "Loading over encrypted DNS…"
    } else {
        "Loading over encrypted DNS… ${(fraction * PERCENT).roundToInt()}%"
    }

internal const val STAGED_FAILED_MESSAGE =
    "This video couldn't be loaded for playback in Strict encrypted DNS mode. Save the file to play it."
private const val MAX_CONCURRENT_STAGED = 2
private const val PERCENT = 100
