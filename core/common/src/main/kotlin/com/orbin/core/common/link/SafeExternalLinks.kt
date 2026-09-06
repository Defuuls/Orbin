package com.orbin.core.common.link

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import java.net.URI

/**
 * Opens external URLs only when they are https and not on the same scheme denylist the comment
 * parsers use. Prefer Custom Tabs; fall back to a plain VIEW intent. Fail closed on anything else.
 */
object SafeExternalLinks {
    /**
     * Schemes rejected by the vichan/LynxChan comment parsers — kept in sync so opening a link
     * cannot be less strict than parsing one.
     */
    private val UNSAFE_LINK_SCHEMES =
        listOf(
            "javascript:",
            "data:",
            "vbscript:",
            "file:",
            "about:",
            "blob:",
            "jar:",
            "intent:",
        )

    /**
     * Returns a https URL safe to hand to a browser, or null when the input must not be opened.
     * Cleartext http is rejected (HTTPS-only), matching the app's network posture.
     */
    fun sanitizeHttps(url: String): String? {
        val trimmed = url.trim()
        if (!hasSafeHttpsPrefix(trimmed)) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        return trimmed.takeIf { isHttpsWithHost(uri) }
    }

    private fun hasSafeHttpsPrefix(url: String): Boolean {
        if (url.isEmpty()) return false
        val lowered = url.lowercase()
        if (!lowered.startsWith("https://")) return false
        return UNSAFE_LINK_SCHEMES.none { lowered.startsWith(it) }
    }

    private fun isHttpsWithHost(uri: URI): Boolean =
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()

    /** Opens [url] when [sanitizeHttps] accepts it. Returns false when nothing was launched. */
    fun open(
        context: Context,
        url: String,
    ): Boolean {
        val safe = sanitizeHttps(url) ?: return false
        val uri = Uri.parse(safe)
        return runCatching {
            CustomTabsIntent.Builder().build().launchUrl(context, uri)
            true
        }.recoverCatching {
            val intent =
                Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
