package com.orbin.ios

import androidx.compose.runtime.Composable
import com.orbin.core.model.MediaType
import com.orbin.core.model.PlaybackPreferences

/** Returns foreground visibility and keeps exactly one permitted upcoming clip while visible. */
@Composable
internal expect fun FeedPreloadLifecycle(
    preloader: NativeMediaPreloader,
    target: MediaPreloadKey?,
): Boolean

/** Both source-qualified scopes consent before an adjacent visible clip is fetched. */
internal fun feedPreloadTarget(
    current: FeedThread?,
    upcoming: FeedThread?,
    preferences: PlaybackPreferences,
    supportsWebM: Boolean,
): MediaPreloadKey? {
    val currentKey = current?.thread?.key ?: return null
    val next = upcoming ?: return null
    val nextKey = next.thread.key
    val bothScopesPermit =
        preferences.resolve(currentKey.provider, currentKey.board).preloadNextClip &&
            preferences.resolve(nextKey.provider, nextKey.board).preloadNextClip
    return next.thread.originalPost.attachments
        .firstOrNull()
        ?.takeIf { bothScopesPermit && !it.isSpoiler && it.type == MediaType.VIDEO }
        ?.preloadKey()
        ?.takeIf { !it.webM || supportsWebM }
        ?.let { it.copy(id = "${next.feedRowId}/${it.id}") }
}
