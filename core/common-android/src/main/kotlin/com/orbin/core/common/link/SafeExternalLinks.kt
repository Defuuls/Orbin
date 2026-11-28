package com.orbin.core.common.link

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.orbin.domain.link.LinkPolicy

/**
 * Opens external URLs that pass the shared HTTPS rule ([LinkPolicy.sanitize]), in a Custom Tab
 * when one is available and otherwise with a plain VIEW intent. Fails closed on anything else.
 */
object SafeExternalLinks {
    /** Returns a https URL safe to hand to a browser, or null when the input must not be opened. */
    fun sanitizeHttps(url: String): String? = LinkPolicy.sanitize(url)

    /** Opens [url] when [sanitizeHttps] accepts it. Returns false when nothing was launched. */
    fun open(
        context: Context,
        url: String,
    ): Boolean {
        val safe = sanitizeHttps(url) ?: return false
        val uri = safe.toUri()
        return runCatching {
            CustomTabsIntent
                .Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .build()
                .launchUrl(context, uri)
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
