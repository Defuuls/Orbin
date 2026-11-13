package com.orbin.ios

import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType

/** Complete media identity: attachment IDs and board slugs are not globally unique. */
internal data class MediaPreloadKey(
    val id: String,
    val url: String,
    val type: MediaType,
    val extension: String,
) {
    val webM: Boolean get() = extension == "webm"
}

/** Only formats handled by the app can own an upcoming native player or WebKit view. */
internal fun MediaAttachment.preloadKey(): MediaPreloadKey? {
    if (!playsInApp && !isWebM) return null
    val url = safeExternalLink(sourceUrl) ?: return null
    return MediaPreloadKey(id, url, type, extension.lowercase().removePrefix("."))
}

/** Pick one upcoming clip without revealing spoilers or starting work for an invalid viewport. */
internal fun nextMediaPreloadTarget(
    files: List<MediaAttachment>,
    currentIndex: Int,
    enabled: Boolean,
    supportsWebM: Boolean,
    currentReady: Boolean = true,
): MediaPreloadKey? {
    if (!enabled || currentIndex !in files.indices) return null
    val current = files[currentIndex]
    val currentPlayable = current.playsInApp || (current.isWebM && supportsWebM)
    if (!currentReady && currentPlayable) return null
    for (index in currentIndex + 1 until files.size) {
        val file = files[index]
        if (file.isSpoiler) continue
        val key = file.preloadKey() ?: continue
        if (!key.webM || supportsWebM) return key
    }
    return null
}

/**
 * The viewport owns at most one upcoming resource. Promotion transfers ownership to the visible
 * player; replacement, leaving the viewer, and disabling preload release only unclaimed work.
 * All calls belong to the UI thread, as do the native resources this owner coordinates.
 */
internal class SingleUpcomingMedia<T : Any>(
    private val create: (MediaPreloadKey) -> T,
    private val release: (T) -> Unit,
) {
    private var upcoming: Pair<MediaPreloadKey, T>? = null

    val key: MediaPreloadKey? get() = upcoming?.first

    fun prepare(target: MediaPreloadKey?) {
        if (target == key) return
        val previous = upcoming
        upcoming = null
        previous?.second?.let(release)
        if (target != null) upcoming = target to create(target)
    }

    /** Inspect preparation without transferring its cancellation/disposal responsibility. */
    fun peek(target: MediaPreloadKey): T? = upcoming?.takeIf { it.first == target }?.second

    /** The caller becomes responsible for releasing a successfully claimed resource. */
    fun claim(target: MediaPreloadKey): T? {
        val prepared = upcoming?.takeIf { it.first == target } ?: return null
        upcoming = null
        return prepared.second
    }

    fun clear() {
        prepare(null)
    }
}
