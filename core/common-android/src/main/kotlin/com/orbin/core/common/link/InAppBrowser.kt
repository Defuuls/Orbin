package com.orbin.core.common.link

import android.content.Context
import android.content.Intent
import android.webkit.WebView
import androidx.core.net.toUri
import com.orbin.domain.link.LinkPolicy

/**
 * The contract between the app and the in-app browser, which runs in its own process.
 *
 * The browser loads arbitrary sites, so it is kept apart from the reader: a separate process with
 * a separate WebView data directory ([WebView.setDataDirectorySuffix]). The reader's startup wipe
 * of the search's web data cannot reach the browser's saved logins, a page that crashes or
 * exhausts memory takes down only the browser, and nothing the browser process does touches the
 * reader's settings store, which is not safe to open from two processes. Everything the browser
 * needs from settings therefore travels in the launch intent.
 *
 * Callers find the browser by action and package, so features open it without depending on it.
 */
object InAppBrowser {
    const val ACTION_OPEN = "com.orbin.action.OPEN_IN_APP_BROWSER"
    const val ACTION_CLEAR_DATA = "com.orbin.action.CLEAR_IN_APP_BROWSER_DATA"

    /** The `android:process` the browser's activity and service run in. */
    const val PROCESS_SUFFIX = ":browser"

    /** Gives the browser process its own WebView data and cache directories. */
    const val DATA_DIRECTORY_SUFFIX = "browser"

    const val EXTRA_DARK = "com.orbin.browser.DARK"
    const val EXTRA_AMOLED = "com.orbin.browser.AMOLED"
    const val EXTRA_SECURE = "com.orbin.browser.SECURE"
    const val EXTRA_CLOSE_WHEN_HIDDEN = "com.orbin.browser.CLOSE_WHEN_HIDDEN"

    /** How the reader is set up, applied to the browser so it looks and protects the same way. */
    data class Appearance(
        val dark: Boolean,
        val amoled: Boolean,
        /** Keep the page out of screenshots and the recents preview (screen privacy). */
        val secure: Boolean,
        /**
         * Close the browser whenever it leaves the screen. Set while the app lock is on: the lock
         * covers only the reader, so a browser left open would otherwise show without unlocking.
         */
        val closeWhenHidden: Boolean,
    )

    /** The intent that opens [url] in the browser, or null when [url] is not a safe link. */
    fun openIntent(
        context: Context,
        url: String,
        appearance: Appearance,
    ): Intent? {
        val safe = LinkPolicy.sanitize(url) ?: return null
        return Intent(ACTION_OPEN, safe.toUri())
            .setPackage(context.packageName)
            .putExtra(EXTRA_DARK, appearance.dark)
            .putExtra(EXTRA_AMOLED, appearance.amoled)
            .putExtra(EXTRA_SECURE, appearance.secure)
            .putExtra(EXTRA_CLOSE_WHEN_HIDDEN, appearance.closeWhenHidden)
    }

    /** Reads what [openIntent] wrote; anything missing takes the safer value. */
    fun appearance(intent: Intent): Appearance =
        Appearance(
            dark = intent.getBooleanExtra(EXTRA_DARK, false),
            amoled = intent.getBooleanExtra(EXTRA_AMOLED, false),
            secure = intent.getBooleanExtra(EXTRA_SECURE, true),
            closeWhenHidden = intent.getBooleanExtra(EXTRA_CLOSE_WHEN_HIDDEN, true),
        )

    /** Starts the browser process's service that deletes its cookies, site data and cache. */
    fun clearDataIntent(context: Context): Intent = Intent(ACTION_CLEAR_DATA).setPackage(context.packageName)

    /** Whether [processName] is the browser process of the app [packageName]. */
    fun isBrowserProcess(
        processName: String?,
        packageName: String,
    ): Boolean = processName == packageName + PROCESS_SUFFIX

    /**
     * Must run in the browser process before anything there creates a WebView; the application
     * calls it first thing in that process.
     */
    fun useBrowserDataDirectory() {
        WebView.setDataDirectorySuffix(DATA_DIRECTORY_SUFFIX)
    }
}
