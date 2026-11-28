package com.orbin.ios

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.UriHandler
import com.orbin.domain.link.LinkPolicy
import com.orbin.domain.link.LinkRoute
import com.orbin.uinext.screens.BrowserActions
import com.orbin.uinext.screens.BrowserChromeState
import com.orbin.uinext.screens.BrowserScreen

/**
 * Every link in the iOS app opens through this one handler, installed as Compose's
 * `LocalUriHandler`, as Android's `OrbinUriHandler` is: comment links, file sources, "Open in
 * browser" and Settings links. [LinkPolicy] decides where; an unsafe link opens nothing.
 * [openLinksInApp] is read at tap time, so a changed setting applies at once, and [external] is
 * the system's own handler, which opens the default browser.
 */
internal class ReaderUriHandler(
    private val openLinksInApp: () -> Boolean,
    private val inAppAvailable: Boolean,
    private val openInApp: (String) -> Unit,
    private val external: UriHandler,
) : UriHandler {
    override fun openUri(uri: String) {
        when (val route = LinkPolicy.route(uri, openLinksInApp(), inAppAvailable)) {
            is LinkRoute.InApp -> openInApp(route.url)
            is LinkRoute.External -> external.openUri(route.url)
            LinkRoute.Rejected -> Unit
        }
    }
}

/**
 * Whether the in-app browser may load [url]. The page itself follows the one link rule
 * ([LinkPolicy.allowsInAppPage]), so a page cannot lead to cleartext http, a local file, or a
 * scheme that opens another app. Frames inside the page may also be blank documents, which many
 * sites build widgets in; anything else is refused there too.
 */
internal fun allowsInAppBrowserNavigation(
    url: String?,
    mainFrame: Boolean,
): Boolean {
    if (url != null && LinkPolicy.allowsInAppPage(url)) return true
    if (mainFrame) return false
    val lower = url?.trim()?.lowercase() ?: return false
    return lower == "about:blank" || lower == "about:srcdoc"
}

/** What the browser frame shows for a page in its current state. */
internal fun webPageChrome(
    url: String?,
    title: String?,
    loading: Boolean,
    progress: Double,
    canGoBack: Boolean,
    canGoForward: Boolean,
): BrowserChromeState =
    BrowserChromeState(
        title = title.orEmpty(),
        url = url.orEmpty(),
        progress = if (loading) progress.toFloat().coerceIn(0f, 1f) else null,
        canGoBack = canGoBack,
        canGoForward = canGoForward,
    )

/** One page in the in-app browser: the platform's web view and what the frame shows about it. */
internal interface InAppWebPage {
    /** Compose state: reading it in composition redraws the frame when the page changes. */
    val chrome: BrowserChromeState

    fun reload()

    fun goBack()

    fun goForward()

    @Composable
    fun Content(modifier: Modifier)
}

/** Whether this platform build has the in-app browser; where it does not, links open externally. */
internal expect val supportsInAppBrowser: Boolean

/** A web page that starts at [url] and lasts while it is in composition. */
@Composable
internal expect fun rememberInAppWebPage(url: String): InAppWebPage

/** Deletes the in-app browser's cookies, site storage and cache: sites' logins included. */
internal expect suspend fun clearInAppBrowserData()

/**
 * The in-app browser screen. It is a page in the app's own navigation, so App lock's cover, the
 * screen privacy shield and the theme apply to it as to every other screen, and closing it returns
 * to where the link was tapped. "Open in browser" uses [external], whatever the setting.
 */
@Composable
internal fun WebPageDestination(
    browser: Browser,
    route: Route.WebPage,
    external: UriHandler,
) {
    val page = rememberInAppWebPage(route.url)
    val current = { page.chrome.url.ifBlank { route.url } }
    BrowserScreen(
        state = page.chrome,
        actions =
            BrowserActions(
                onClose = { browser.back() },
                onReload = page::reload,
                onBack = page::goBack,
                onForward = page::goForward,
                onShare = { LinkPolicy.sanitize(current())?.let { shareContent(page.chrome.title, it) } },
                onOpenExternal = { LinkPolicy.sanitize(current())?.let(external::openUri) },
            ),
    ) { modifier -> page.Content(modifier) }
}
