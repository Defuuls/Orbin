package com.orbin.media.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import com.orbin.media.di.VideoMediaDataSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

// [VideoPlayer]'s plumbing: its data source, fullscreen window, and the pure rules it plays by.

internal fun Context.videoMediaDataSourceFactory(): DataSource.Factory =
    EntryPointAccessors.fromApplication(this, VideoPlayerEntryPoint::class.java).videoDataSourceFactory()

/** Unwraps the hosting [Activity] from a (possibly wrapped) composition [Context], if any. */
internal tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/** Hides the system bars for fullscreen playback, or brings them back. */
internal fun Activity.applyVideoFullscreen(fullscreen: Boolean) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (fullscreen) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

/**
 * Where a double tap lands: [SKIP_SECONDS] from [currentMs], never before the start and, once the
 * duration is known, never past the end. An unknown duration (still loading, or a live stream)
 * only clamps at zero.
 */
internal fun seekTargetMs(
    currentMs: Long,
    durationMs: Long,
    forward: Boolean,
): Long {
    val step = SKIP_SECONDS * MILLIS_PER_SECOND
    val target = currentMs.coerceAtLeast(0L) + if (forward) step else -step
    return if (durationMs > 0L) target.coerceIn(0L, durationMs) else target.coerceAtLeast(0L)
}

/** Clips up to [LOOP_UNDER_MS] loop like GIFs; longer videos play once. Unknown length loops. */
internal fun shouldLoop(durationMs: Long): Boolean = durationMs <= LOOP_UNDER_MS

internal fun repeatModeFor(loopEnabled: Boolean): Int =
    if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

internal fun Long.progressIn(durationMs: Long): Float =
    if (durationMs > 0L) {
        (toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

internal fun Long.formatTimestamp(): String {
    val totalSeconds = (this / MILLIS_PER_SECOND).coerceAtLeast(0L)
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

internal fun PlaybackException.mediaLoadMessage(): String =
    if (hasHttpStatus(HTTP_TOO_MANY_REQUESTS)) {
        "Video rate limited. Try again later."
    } else {
        "Video unavailable"
    }

internal fun Throwable.hasHttpStatus(statusCode: Int): Boolean =
    generateSequence(this as Throwable?) { it.cause }
        .any { throwable -> throwable.message?.contains(statusCode.toString()) == true }

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface VideoPlayerEntryPoint {
    @VideoMediaDataSource
    fun videoDataSourceFactory(): DataSource.Factory
}
