package com.orbin.uinext.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.orbin.uinext.components.ErrorNote
import com.orbin.uinext.components.InlineAction
import com.orbin.uinext.components.reader
import com.orbin.uinext.tokens.OrbinType

/** Native verification is visible and interactive; result pages use Orbin's filtered cards. */
@Composable
fun WebSearchResultsPane(
    state: SearchState,
    onOpen: (SearchRow) -> Unit,
    onRetry: () -> Unit,
    verifying: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    error: String? = null,
    onOpenBrowser: (() -> Unit)? = null,
    browser: @Composable (Modifier) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (verifying) {
            Text(
                "Complete 4chan's verification to continue searching.",
                style = OrbinType.body,
                color = reader.ink,
                modifier = Modifier.padding(16.dp),
            )
        } else if (error != null && state is SearchState.Results) {
            ErrorNote(error, modifier = Modifier.padding(16.dp))
            InlineAction("Retry", onClick = onRetry)
        }
        if (error != null && onOpenBrowser != null) InlineAction("Open in browser", onClick = onOpenBrowser)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!verifying) {
                if (state is SearchState.Idle) {
                    ReaderEmptyState(title = "All of 4chan", message = "Search thread posts across 4chan.")
                } else {
                    SearchResultsContent(state, onOpen, onRetry)
                }
            }
            // Keep one browser alive for its verification cookies, without exposing unfiltered results.
            browser(if (verifying) Modifier.fillMaxSize() else Modifier.size(1.dp).alpha(0f))
        }
        if (canLoadMore &&
            !verifying
        ) {
            InlineAction("Load more", onClick = onLoadMore, modifier = Modifier.padding(8.dp))
        }
    }
}

/**
 * Shown in place of the "All of 4chan" search while strict encrypted DNS is on.
 *
 * That search loads the site in the system web view, which looks names up with the device's
 * resolver rather than the app's encrypted one. Strict mode exists for readers who would rather a
 * feature stop than have a hostname reach the network, so the host never creates the web view and
 * this says why. Followed-board search is unaffected: it goes through the app's own client.
 */
@Composable
fun WebSearchBlockedPane(modifier: Modifier = Modifier) {
    ReaderEmptyState(
        title = WEB_SEARCH_BLOCKED_TITLE,
        message = WEB_SEARCH_BLOCKED_MESSAGE,
        modifier = modifier,
    )
}

const val WEB_SEARCH_BLOCKED_TITLE = "Off in strict DNS mode"
const val WEB_SEARCH_BLOCKED_MESSAGE =
    "Searching all of 4chan opens the site in a system web view, which uses your device's DNS " +
        "instead of encrypted DNS. Turn off Strict encrypted DNS in Settings to use it. " +
        "Followed boards can still be searched."
