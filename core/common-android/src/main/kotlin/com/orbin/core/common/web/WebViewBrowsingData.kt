package com.orbin.core.common.web

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.annotation.MainThread
import java.io.File

/**
 * Removes what Android's WebView keeps for the sites it has loaded.
 *
 * WebView has no private mode: cookies, site storage and cached responses go to the app's
 * `app_webview` and `cache/WebView` directories, unencrypted, and stay until something deletes
 * them. Orbin's only web view is the "All of 4chan" search, and nothing it stores is wanted after
 * the search closes.
 */
object WebViewBrowsingData {
    /**
     * Clears the live WebView state. [webView] is the view being closed, if there is one; its
     * cache is process-wide, so clearing through any one view clears it for all.
     */
    @MainThread
    fun clear(webView: WebView? = null) {
        webView?.apply {
            clearCache(true)
            clearHistory()
            clearFormData()
        }
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            // Cookies are written to disk lazily; without this a removed cookie can outlive the process.
            flush()
        }
        WebStorage.getInstance().deleteAllData()
    }

    /**
     * Deletes the directories themselves. Only safe before the process creates a WebView, so it
     * runs at startup, where it covers a process that was killed before [clear] could run.
     *
     * @return false when something could not be deleted.
     */
    fun deleteStoredFiles(context: Context): Boolean =
        storedDirectories(context)
            .map { !it.exists() || it.deleteRecursively() }
            .all { it }

    internal fun storedDirectories(context: Context): List<File> =
        listOf(
            File(context.dataDir, "app_webview"),
            File(context.cacheDir, "WebView"),
        )
}
