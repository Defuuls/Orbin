package com.orbin.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.UriHandler
import com.orbin.core.common.link.InAppBrowser
import com.orbin.core.common.link.SafeExternalLinks
import com.orbin.core.model.AppSettings
import com.orbin.domain.link.LinkPolicy
import com.orbin.domain.link.LinkRoute

/**
 * Every link in the app opens through this one handler, installed as Compose's `LocalUriHandler`:
 * comment links, file sources, "Open in browser" and Settings links. [LinkPolicy] decides where;
 * an unsafe link opens nothing. [settings] is read at tap time, so a changed setting applies at
 * once.
 */
internal class OrbinUriHandler(
    private val context: Context,
    private val settings: () -> AppSettings,
    private val dark: () -> Boolean,
    private val startBrowser: (Intent) -> Unit = { context.startActivity(it) },
    private val openExternal: (String) -> Boolean = { SafeExternalLinks.open(context, it) },
) : UriHandler {
    override fun openUri(uri: String) {
        val current = settings()
        when (val route = LinkPolicy.route(uri, current.openLinksInApp, inAppAvailable = true)) {
            is LinkRoute.InApp -> if (!openInApp(route.url, current)) openExternal(route.url)
            is LinkRoute.External -> openExternal(route.url)
            LinkRoute.Rejected -> Unit
        }
    }

    /** False when the browser could not be started, so the link still opens externally. */
    private fun openInApp(
        url: String,
        settings: AppSettings,
    ): Boolean {
        val intent = InAppBrowser.openIntent(context, url, browserAppearance(settings, dark())) ?: return false
        return try {
            startBrowser(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}

/** The reader's look and protections, carried to the browser process in the launch intent. */
internal fun browserAppearance(
    settings: AppSettings,
    dark: Boolean,
): InAppBrowser.Appearance =
    InAppBrowser.Appearance(
        dark = dark,
        amoled = settings.amoled,
        // App lock also keeps the page out of the recents preview, as it does for the reader.
        secure = settings.screenPrivacyEnabled || settings.biometricLockEnabled,
        closeWhenHidden = settings.biometricLockEnabled,
    )
