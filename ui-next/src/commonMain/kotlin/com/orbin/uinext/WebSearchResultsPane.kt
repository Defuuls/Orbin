package com.orbin.uinext

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
