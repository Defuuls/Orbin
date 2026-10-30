package com.orbin.app

import android.content.Context

/** Run on every upgrade launch, even when no image or video is ever opened. */
internal fun retireLegacyBrowsingCaches(context: Context) {
    for (name in listOf("http-cache", "image_cache", "media3-video-cache")) {
        val directory = context.cacheDir.resolve(name)
        check(!directory.exists() || directory.deleteRecursively()) { "Could not retire legacy browsing cache" }
    }
    // Media3's standalone database indexes cached URL keys separately from the media files.
    check(
        context.deleteDatabase("exoplayer_internal.db") || !context.getDatabasePath("exoplayer_internal.db").exists(),
    ) {
        "Could not retire legacy video cache index"
    }
}
