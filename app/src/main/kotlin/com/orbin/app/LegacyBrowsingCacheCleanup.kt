package com.orbin.app

import android.content.Context
import co.touchlab.kermit.Logger
import com.orbin.core.common.web.WebViewBrowsingData

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

/**
 * The search web view clears its cookies and site storage when it closes, but a process that is
 * killed first never gets there. Nothing has created a WebView this early in startup, so what a
 * previous process left can be deleted as plain files. A failure is not worth refusing to start
 * over: the next search clears the same state through WebView itself.
 */
internal fun removeLeftoverWebViewData(context: Context) {
    if (!WebViewBrowsingData.deleteStoredFiles(context)) {
        Logger.w(tag = "OrbinApplication") { "Could not remove leftover web view data" }
    }
}
