@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.orbin.ios

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.asset
import platform.AVFoundation.cancelLoading
import platform.AVFoundation.currentItem
import platform.AVFoundation.pause
import platform.AVFoundation.preferredForwardBufferDuration
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.WebKit.WKWebView

internal data class PlaybackBookmark(
    val position: Double,
    val playing: Boolean,
    val muted: Boolean,
    val command: Int = 0,
)

internal data class NativePlaybackHandoff(
    val player: AVPlayer,
    val bookmark: PlaybackBookmark,
)

internal data class WebMPlaybackHandoff(
    val view: WKWebView,
    val bookmark: PlaybackBookmark,
)

/** Main-thread, session-only handoff. Live owners never share a player or WebKit surface. */
internal object NativePlaybackContinuity {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val bookmarks = linkedMapOf<String, PlaybackBookmark>()
    private val requested = linkedSetOf<String>()
    private var native: Pair<String, NativePlaybackHandoff>? = null
    private var webM: Pair<String, WebMPlaybackHandoff>? = null
    private var nativeExpiry: Job? = null
    private var webMExpiry: Job? = null

    init {
        NativeVideoCaches.register(::clear)
    }

    fun request(url: String) {
        requested += url
        while (requested.size > MAX_HANDOFF_REQUESTS) requested.remove(requested.first())
    }

    fun bookmark(url: String): PlaybackBookmark? = bookmarks[url]?.takeIf { url in requested }

    fun remember(
        url: String,
        bookmark: PlaybackBookmark,
    ) {
        if (url.startsWith("data:")) return
        bookmarks.remove(url)
        bookmarks[url] = bookmark
        while (bookmarks.size > MAX_PLAYBACK_BOOKMARKS) bookmarks.remove(bookmarks.keys.first())
    }

    fun takeNative(url: String): NativePlaybackHandoff? {
        val retained = native?.takeIf { it.first == url } ?: return null
        native = null
        requested.remove(url)
        nativeExpiry?.cancel()
        nativeExpiry = null
        return retained.second
    }

    fun takeWebM(url: String): WebMPlaybackHandoff? {
        val retained = webM?.takeIf { it.first == url } ?: return null
        webM = null
        requested.remove(url)
        webMExpiry?.cancel()
        webMExpiry = null
        return retained.second
    }

    /**
     * Keeps [player] for the viewer when it asked for [url]; otherwise it is done with, and goes
     * to [recycle] (an inline tile's pool) or is released.
     */
    fun retain(
        url: String,
        player: AVPlayer,
        bookmark: PlaybackBookmark,
        recycle: ((AVPlayer) -> Unit)? = null,
    ) {
        remember(url, bookmark)
        player.pause()
        // Local library staging is removed by its owner immediately on dismissal.
        if (!requested.remove(url) || "/Orbin/MediaExport/" in url || url.startsWith("data:")) {
            if (recycle != null) recycle(player) else release(player)
            return
        }
        nativeExpiry?.cancel()
        native?.second?.player?.let(::release)
        webMExpiry?.cancel()
        webM?.second?.view?.tearDownMedia()
        webM = null
        player.currentItem?.preferredForwardBufferDuration = 2.0
        native = url to NativePlaybackHandoff(player, bookmark)
        nativeExpiry =
            scope.launch {
                delay(HANDOFF_LIFETIME_MILLIS)
                native?.second?.player?.let(::release)
                native = null
            }
    }

    fun retain(
        url: String,
        view: WKWebView,
        bookmark: PlaybackBookmark,
    ) {
        remember(url, bookmark)
        view.navigationDelegate = null
        view.UIDelegate = null
        // Invalidate pending play promises before detaching; a transferred surface keeps its clock.
        view.evaluateJavaScript(
            webMPlaybackScript(WebMPlaybackRequest(bookmark.command + 1, false, bookmark.muted, false)),
            null,
        )
        if (!requested.remove(url) || "/Orbin/MediaExport/" in url || url.startsWith("data:")) {
            view.tearDownMedia()
            return
        }
        webMExpiry?.cancel()
        webM?.second?.view?.tearDownMedia()
        nativeExpiry?.cancel()
        native?.second?.player?.let(::release)
        native = null
        webM = url to WebMPlaybackHandoff(view, bookmark.copy(command = bookmark.command + 1))
        webMExpiry =
            scope.launch {
                delay(HANDOFF_LIFETIME_MILLIS)
                webM?.second?.view?.tearDownMedia()
                webM = null
            }
    }

    fun clear() {
        nativeExpiry?.cancel()
        webMExpiry?.cancel()
        native?.second?.player?.let(::release)
        webM?.second?.view?.tearDownMedia()
        native = null
        webM = null
        bookmarks.clear()
        requested.clear()
    }

    fun release(player: AVPlayer) {
        player.pause()
        player.currentItem?.asset?.cancelLoading()
        player.replaceCurrentItemWithPlayerItem(null)
    }
}

private const val MAX_HANDOFF_REQUESTS = 4
private const val MAX_PLAYBACK_BOOKMARKS = 32
private const val HANDOFF_LIFETIME_MILLIS = 15_000L
