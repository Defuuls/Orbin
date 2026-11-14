package com.orbin.media.video

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.orbin.media.di.VideoMediaEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * One video shared by every surface that displays it (inline card and fullscreen viewer),
 * translated from NekoSurf's `MediaVideo` (PR #89).
 *
 * Opening a video fullscreen continues the exact same playback and [VideoServer] stream without
 * reloading from scratch, and closing returns control smoothly within a 250ms grace window.
 */
class MediaVideo private constructor(
    val source: String,
    initialMuted: Boolean,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refs: Int = 0

    // Owner -> whether owner wants audio (fullscreen = true, inline = false).
    private val demands = mutableMapOf<Any, Boolean>()
    private val shown = mutableMapOf<Any, Float>()

    @Volatile
    var muted: Boolean = initialMuted
        set(value) {
            field = value
            applyVolume()
        }

    @Volatile
    var rate: Float = 1f
        set(value) {
            field = value
            lease?.player?.setPlaybackSpeed(value)
        }

    @Volatile
    var loop: Boolean = true
        set(value) {
            field = value
            lease?.player?.repeatMode = repeatModeFor(value)
        }

    private var userPaused: Boolean = false
    private var lease: VideoLease? = null
    private var pauseTimer: Job? = null
    private var progressJob: Job? = null
    private var resumeAtMs: Long = 0L
    private var scrubbing: Boolean = false
    private var playingBeforeScrub: Boolean = false
    private var lastServer: VideoServer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _hasFirstFrame = MutableStateFlow(false)
    val hasFirstFrame: StateFlow<Boolean> = _hasFirstFrame.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _scrubPositionMs = MutableStateFlow<Long?>(null)
    val scrubPositionMs: StateFlow<Long?> = _scrubPositionMs.asStateFlow()

    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    val currentPlayer: Player? get() = lease?.player

    val priority: Float
        get() {
            var maxPriority = 0f
            for ((owner, wantsAudio) in demands) {
                val weight = if (wantsAudio) 2f else (shown[owner] ?: 1f)
                maxPriority = max(maxPriority, weight)
            }
            return maxPriority
        }

    fun want(
        context: Context,
        owner: Any,
        audible: Boolean = false,
        shownFraction: Float = 1f,
    ) {
        val wasAudible = demands[owner]
        demands[owner] = audible
        shown[owner] = shownFraction.coerceIn(0f, 1f)
        if (wasAudible == null) {
            userPaused = false
        }
        pauseTimer?.cancel()
        pauseTimer = null
        sync(context)
    }

    fun unwant(owner: Any) {
        shown.remove(owner)
        if (demands.remove(owner) == null) return
        if (demands.isEmpty()) {
            pauseTimer?.cancel()
            pauseTimer =
                scope.launch {
                    delay(PAUSE_GRACE_MS)
                    if (demands.isEmpty()) {
                        releaseLease()
                        lastServer?.unwatch(source)
                    }
                }
        } else {
            applyVolume()
            lease?.priority = priority
        }
    }

    fun togglePlay(context: Context) {
        val player = lease?.player
        if (_isPlaying.value) {
            userPaused = true
            player?.pause()
            return
        }
        userPaused = false
        if (player != null) {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0L)
            }
            player.play()
        } else {
            sync(context)
        }
    }

    fun startScrub() {
        val player = lease?.player ?: return
        scrubbing = true
        playingBeforeScrub = _isPlaying.value
        player.pause()
    }

    fun scrubTo(targetMs: Long) {
        val clamped = if (_durationMs.value > 0L) targetMs.coerceIn(0L, _durationMs.value) else targetMs.coerceAtLeast(0L)
        _scrubPositionMs.value = clamped
        lease?.player?.seekTo(clamped)
    }

    fun endScrub() {
        scrubbing = false
        _scrubPositionMs.value = null
        if (playingBeforeScrub && !userPaused) {
            lease?.player?.play()
        }
    }

    fun prefetch(context: Context) {
        if (!source.startsWith("http")) return
        val server = serverFor(context)
        lastServer = server
        scope.launch { server.prefetch(source) }
    }

    fun release(owner: Any) {
        unwant(owner)
        refs--
        if (refs <= 0) {
            pauseTimer?.cancel()
            releaseLease()
            lastServer?.unwatch(source)
            synchronized(allVideos) {
                if (allVideos[source] === this) {
                    allVideos.remove(source)
                }
            }
        }
    }

    private fun sync(context: Context) {
        if (demands.isEmpty() || userPaused) return
        val existing = lease
        if (existing != null) {
            existing.wanted = true
            existing.priority = priority
            applyVolume()
            if (!existing.player.isPlaying && !scrubbing) {
                existing.player.play()
            }
            return
        }
        val appContext = context.applicationContext
        val server = serverFor(appContext)
        lastServer = server
        val newLease =
            VideoPlayers.acquire(
                context = appContext,
                mediaSourceFactory = VideoPlayers.defaultMediaSourceFactory(appContext),
                priority = priority,
                muted = !anyAudible() && muted,
                onEvicted = {
                    resumeAtMs = lease?.player?.currentPosition?.coerceAtLeast(0L) ?: resumeAtMs
                    progressJob?.cancel()
                    lease = null
                    _player.value = null
                    _isPlaying.value = false
                    _isBuffering.value = false
                },
            ) ?: return

        lease = newLease
        val player = newLease.player
        _player.value = player
        player.repeatMode = repeatModeFor(loop)
        player.setPlaybackSpeed(rate)
        applyVolume()
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _isBuffering.value = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_READY) {
                        val d = player.duration.coerceAtLeast(0L)
                        if (d > 0L) _durationMs.value = d
                    }
                }

                override fun onRenderedFirstFrame() {
                    _hasFirstFrame.value = true
                    _failed.value = false
                }
            },
        )
        progressJob?.cancel()
        progressJob =
            scope.launch {
                while (true) {
                    if (!scrubbing) {
                        _positionMs.value = player.currentPosition.coerceAtLeast(0L)
                    }
                    delay(50L)
                }
            }

        scope.launch {
            val resolvedUri = if (source.startsWith("http")) server.source(source) else source
            if (lease !== newLease) return@launch
            player.setMediaItem(MediaItem.fromUri(resolvedUri))
            if (resumeAtMs > 0L) {
                player.seekTo(resumeAtMs)
            }
            player.prepare()
            player.playWhenReady = !userPaused && demands.isNotEmpty()
        }
    }

    private fun releaseLease() {
        progressJob?.cancel()
        progressJob = null
        val current = lease ?: return
        resumeAtMs = current.player.currentPosition.coerceAtLeast(0L)
        lease = null
        _player.value = null
        _isPlaying.value = false
        _isBuffering.value = false
        current.release()
    }

    private fun anyAudible(): Boolean = demands.values.any { it }

    private fun applyVolume() {
        val player = lease?.player ?: return
        val shouldMute = muted && !anyAudible()
        player.volume = if (shouldMute) 0f else 1f
    }

    private fun serverFor(context: Context): VideoServer =
        EntryPointAccessors.fromApplication(context.applicationContext, VideoMediaEntryPoint::class.java).videoServer()

    companion object {
        private const val PAUSE_GRACE_MS = 250L
        private val allVideos = mutableMapOf<String, MediaVideo>()

        fun obtain(
            source: String,
            muted: Boolean = true,
        ): MediaVideo =
            synchronized(allVideos) {
                val video =
                    allVideos.getOrPut(source) {
                        MediaVideo(source = source, initialMuted = muted)
                    }
                video.refs++
                video
            }
    }
}
