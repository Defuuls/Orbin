package com.orbin.uinext.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.orbin.uinext.components.ReaderAction
import com.orbin.uinext.components.ReaderAppBar
import com.orbin.uinext.components.ReaderIcons
import com.orbin.uinext.components.ReaderMenuItem
import com.orbin.uinext.components.ReaderNavigation
import com.orbin.uinext.components.reader
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.browser_back
import com.orbin.uinext.resources.browser_forward
import com.orbin.uinext.resources.browser_loading
import com.orbin.uinext.resources.browser_open_external
import com.orbin.uinext.resources.browser_reload
import com.orbin.uinext.resources.browser_share
import com.orbin.uinext.tokens.ReaderSize
import org.jetbrains.compose.resources.stringResource

object BrowserTags {
    const val ROOT = "browser-root"
    const val PROGRESS = "browser-progress"
    const val PAGE = "browser-page"
}

/**
 * What the in-app browser's frame shows about the page. The platform host owns the web view and
 * reports these; the frame itself never touches web content.
 */
data class BrowserChromeState(
    val title: String = "",
    /** The page's address, shown under the title so the reader always sees which site this is. */
    val url: String = "",
    /** Load progress from 0 to 1, or null when the page has finished loading. */
    val progress: Float? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

class BrowserActions(
    val onClose: () -> Unit,
    val onReload: () -> Unit,
    val onBack: () -> Unit = {},
    val onForward: () -> Unit = {},
    val onShare: (() -> Unit)? = null,
    val onOpenExternal: (() -> Unit)? = null,
)

/**
 * The in-app browser's frame, shared by Android and iOS: the page's title and address, reload,
 * back and forward, share and "Open in browser". [page] draws the platform's web view.
 */
@Composable
fun BrowserScreen(
    state: BrowserChromeState,
    actions: BrowserActions,
    modifier: Modifier = Modifier,
    page: @Composable (Modifier) -> Unit,
) {
    val colors = reader
    val host = browserHost(state.url)
    val back = stringResource(Res.string.browser_back)
    val forward = stringResource(Res.string.browser_forward)
    val share = stringResource(Res.string.browser_share)
    val openExternal = stringResource(Res.string.browser_open_external)
    val loading = stringResource(Res.string.browser_loading)
    Column(modifier.fillMaxSize().background(colors.background).testTag(BrowserTags.ROOT)) {
        val title = state.title.ifBlank { host }
        ReaderAppBar(
            title = title,
            // The address is always visible: a page title alone can claim to be any site.
            subtitle = host.takeIf { it.isNotBlank() && it != title },
            // The bar's Back leaves the browser; page history is "Previous page" in the menu,
            // and the system back gesture steps through page history before it closes.
            navigation = ReaderNavigation.BACK,
            onNavigation = actions.onClose,
            actions =
                listOf(
                    ReaderAction(ReaderIcons.Refresh, stringResource(Res.string.browser_reload), actions.onReload),
                ),
            overflow =
                listOfNotNull(
                    ReaderMenuItem(back, enabled = state.canGoBack, onClick = actions.onBack),
                    ReaderMenuItem(forward, enabled = state.canGoForward, onClick = actions.onForward),
                    actions.onShare?.let { ReaderMenuItem(share, icon = ReaderIcons.Share, onClick = it) },
                    actions.onOpenExternal?.let {
                        ReaderMenuItem(
                            openExternal,
                            icon = ReaderIcons.Globe,
                            onClick = it,
                        )
                    },
                ),
        )
        val progress = state.progress
        Box(Modifier.fillMaxWidth().height(ReaderSize.progress)) {
            if (progress != null) {
                val fraction = progress.coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(ReaderSize.progress)
                        .background(colors.accent)
                        .testTag(BrowserTags.PROGRESS)
                        .semantics {
                            contentDescription = loading
                            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                        },
                )
            }
        }
        page(Modifier.fillMaxSize().testTag(BrowserTags.PAGE))
    }
}

/**
 * The host of [url] for display, without scheme, credentials, port or path, or "" when it has
 * none. Lowercased, so a mixed-case lookalike cannot dress up as a familiar name.
 */
fun browserHost(url: String): String {
    val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
    val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    return authority.substringAfterLast('@').substringBefore(':').lowercase()
}
