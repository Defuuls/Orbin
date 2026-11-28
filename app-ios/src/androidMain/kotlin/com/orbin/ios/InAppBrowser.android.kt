package com.orbin.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.orbin.uinext.screens.BrowserChromeState

/** Shared-logic test target only; production Android has its own browser in feature:browser. */
internal actual val supportsInAppBrowser: Boolean = false

@Composable
internal actual fun rememberInAppWebPage(url: String): InAppWebPage = remember(url) { HostTestWebPage(url) }

internal actual suspend fun clearInAppBrowserData() = Unit

/** A page with no web view, so the frame around it can be tested on the host. */
internal class HostTestWebPage(
    url: String,
) : InAppWebPage {
    override var chrome by mutableStateOf(BrowserChromeState(url = url))

    override fun reload() = Unit

    override fun goBack() = Unit

    override fun goForward() = Unit

    @Composable
    override fun Content(modifier: Modifier) {
        Box(modifier)
    }
}
