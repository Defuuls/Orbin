package com.orbin.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orbin.core.model.AppSettings
import com.orbin.core.model.DohProvider
import com.orbin.core.model.allowsSystemNetworkFeatures
import com.orbin.data.settings.EncryptedDnsRequest
import com.orbin.data.settings.encryptedDnsRequest
import com.orbin.uinext.components.reader
import com.orbin.uinext.tokens.OrbinType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What iOS applies for a resolver choice. Encrypted resolution is always required: the system API
 * only uses the chosen DoH resolver when it is, so there is no fallback mode to ask for.
 */
internal fun iosEncryptedDnsRequest(provider: DohProvider): EncryptedDnsRequest =
    encryptedDnsRequest(provider, strict = true)

/**
 * Whether iOS's own media stack may fetch a remote file.
 *
 * The app's requests go through URLSession in this process, where the privacy context makes every
 * lookup use encrypted DNS. Three things on iOS do their networking somewhere else and so use the
 * device's resolver, the system's User-Agent and none of the app's request policy:
 *
 * - AVPlayer and AVURLAsset, which stream MP4 and decode preview frames in a media daemon;
 * - WKWebView, which plays WebM and loads the web search in WebKit's own network process;
 * - background URLSession transfers, which run in a system daemon so they survive suspension.
 *
 * By default they are allowed, because without them video does not stream. With strict encrypted
 * DNS on, the reader has asked for a feature to stop rather than leak a hostname, so a remote
 * address is never handed to any of them. Video still plays: [SystemPlayable] fetches the file
 * through the app's own client and gives the player a local copy, which needs no lookup. Previews
 * and the web search have no such path and stay off.
 *
 * Starts closed until the saved setting has been read, so a strict reader's first screen cannot
 * start a stream in the moment before it loads.
 */
internal object SystemMediaPolicy {
    private val allowed = MutableStateFlow(false)

    /** True when remote media may be streamed by the system players. */
    val streamingAllowed: StateFlow<Boolean> = allowed.asStateFlow()

    fun apply(settings: AppSettings) {
        allowed.value = settings.allowsSystemNetworkFeatures
    }

    /** Whether the system may load [url] right now. */
    fun permits(url: String): Boolean = permitsSystemLoad(url, allowed.value)
}

/** Media that needs no lookup is always playable; anything else only when [systemNetworkAllowed]. */
internal fun permitsSystemLoad(
    url: String,
    systemNetworkAllowed: Boolean,
): Boolean = systemNetworkAllowed || needsNoNameLookup(url)

/**
 * Whether loading [url] cannot involve a DNS lookup: a `file:` path on the device, or a `data:`
 * URL, which carries its bytes inline. Every other scheme, including an unrecognised one, is
 * treated as needing the network, which is the safe guess.
 */
internal fun needsNoNameLookup(url: String): Boolean {
    val trimmed = url.trimStart()
    return LOOKUP_FREE_SCHEMES.any { trimmed.startsWith(it, ignoreCase = true) }
}

private val LOOKUP_FREE_SCHEMES = listOf("file:", "data:")

/** Recomposes the caller when the reader changes the setting while a player is on screen. */
@Composable
internal fun systemLoadPermitted(url: String): Boolean {
    val allowed by SystemMediaPolicy.streamingAllowed.collectAsState()
    return permitsSystemLoad(url, allowed)
}

/**
 * The players' own last line of defence: shown if one is ever handed a remote address under
 * strict encrypted DNS. Callers go through [SystemPlayable], so a reader should not meet it.
 */
@Composable
internal fun StreamingBlockedNotice(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            STREAMING_BLOCKED_MESSAGE,
            style = OrbinType.body,
            color = reader.ink,
            textAlign = TextAlign.Center,
        )
    }
}

internal const val STREAMING_BLOCKED_MESSAGE =
    "This video can't be streamed while Strict encrypted DNS is on, because iOS plays video with " +
        "the device's DNS. Save the file to play it, or turn off Strict encrypted DNS in Settings."
