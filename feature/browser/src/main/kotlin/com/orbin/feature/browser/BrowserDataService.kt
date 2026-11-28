package com.orbin.feature.browser

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.webkit.WebView
import com.orbin.core.common.link.InAppBrowser
import com.orbin.core.common.web.WebViewBrowsingData

/**
 * Deletes the browser's cookies, site storage and cache. It runs in the browser process because
 * that process alone can reach the browser's WebView data directory; the reader starts it for
 * "Clear local activity" ([InAppBrowser.clearDataIntent]).
 */
class BrowserDataService : Service() {
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == InAppBrowser.ACTION_CLEAR_DATA) {
            // The cache can only be cleared through a view; this one is never shown.
            val view = WebView(applicationContext)
            try {
                WebViewBrowsingData.clear(view)
            } finally {
                view.destroy()
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
