package com.orbin.media.video

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bounded app-wide pool of [ExoPlayer] instances, translated from NekoSurf's `VideoPlayers` (PR #89).
 *
 * At most [MAX_PLAYERS] players exist at once. Holders acquire a [VideoLease] with a [priority]
 * (e.g. visible fraction `0..1` for inline cards, `2` for fullscreen). When all slots are full,
 * the lowest-priority lease is evicted if it is unwanted or sufficiently less wanted than the
 * requester.
 */
@Suppress("UnsafeOptInUsageError")
object VideoPlayers {
    const val MAX_PLAYERS = 4
    private const val TAKE_OVER_MARGIN = 0.25f

    private val lock = Any()
    private val leases = mutableListOf<VideoLease>()
    private val pausedWhileInactive = mutableSetOf<VideoLease>()
    private val _availability = MutableStateFlow(0L)
    val availability: StateFlow<Long> = _availability.asStateFlow()

    @Volatile
    var pausesWhileInactive: Boolean = false

    @Volatile
    private var inBackground: Boolean = false

    fun setInBackground(background: Boolean) {
        if (background) {
            releaseAll()
        } else {
            inBackground = false
            notifyAvailability()
        }
    }

    fun pauseWhileInactive() {
        if (!pausesWhileInactive) return
        synchronized(lock) {
            for (lease in leases) {
                if (lease.player.isPlaying) {
                    pausedWhileInactive.add(lease)
                    lease.player.pause()
                }
            }
        }
    }

    fun playAfterInactive() {
        synchronized(lock) {
            for (lease in pausedWhileInactive) {
                if (lease in leases) {
                    lease.player.play()
                }
            }
            pausedWhileInactive.clear()
        }
    }

    fun canAcquire(priority: Float): Boolean =
        synchronized(lock) {
            !inBackground && (leases.size < MAX_PLAYERS || victimForLocked(priority) != null)
        }

    fun acquire(
        context: Context,
        mediaSourceFactory: DefaultMediaSourceFactory,
        priority: Float,
        muted: Boolean,
        onEvicted: () -> Unit,
    ): VideoLease? {
        val victim: VideoLease?
        synchronized(lock) {
            if (inBackground) return null
            if (leases.size >= MAX_PLAYERS) {
                victim = victimForLocked(priority) ?: return null
                leases.remove(victim)
                pausedWhileInactive.remove(victim)
            } else {
                victim = null
            }
        }
        victim?.evict()

        val loadControl =
            DefaultLoadControl
                .Builder()
                .setBufferDurationsMs(
                    1_500,
                    8_000,
                    500,
                    1_000,
                ).build()
        val player =
            ExoPlayer
                .Builder(context.applicationContext)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    !muted,
                ).build()
        val lease =
            VideoLease(
                player = player,
                onEvicted = onEvicted,
                initialPriority = priority,
            )
        synchronized(lock) {
            leases.add(lease)
        }
        return lease
    }

    fun release(lease: VideoLease) {
        val removed =
            synchronized(lock) {
                pausedWhileInactive.remove(lease)
                leases.remove(lease)
            }
        if (removed) {
            runCatching {
                lease.player.stop()
                lease.player.release()
            }
            notifyAvailability()
        }
    }

    fun releaseAll() {
        val snapshot =
            synchronized(lock) {
                inBackground = true
                pausedWhileInactive.clear()
                val copy = leases.toList()
                leases.clear()
                copy
            }
        snapshot.forEach { it.evict() }
        notifyAvailability()
    }

    private fun victimForLocked(priority: Float): VideoLease? {
        var victim: VideoLease? = null
        for (lease in leases) {
            if (victim == null || lease.priority < victim.priority) {
                victim = lease
            }
        }
        if (victim != null && victim.wanted && victim.priority > priority - TAKE_OVER_MARGIN) {
            return null
        }
        return victim
    }

    private fun notifyAvailability() {
        _availability.value = _availability.value + 1L
    }

    fun defaultMediaSourceFactory(context: Context): DefaultMediaSourceFactory =
        DefaultMediaSourceFactory(context.videoMediaDataSourceFactory(), DefaultExtractorsFactory())
}

class VideoLease internal constructor(
    val player: ExoPlayer,
    private val onEvicted: () -> Unit,
    initialPriority: Float,
) {
    @Volatile
    var priority: Float = initialPriority

    @Volatile
    var wanted: Boolean = true

    @Volatile
    private var released: Boolean = false

    fun release() {
        if (released) return
        released = true
        VideoPlayers.release(this)
    }

    internal fun evict() {
        if (released) return
        released = true
        runCatching { onEvicted() }
        runCatching {
            player.stop()
            player.release()
        }
    }
}
