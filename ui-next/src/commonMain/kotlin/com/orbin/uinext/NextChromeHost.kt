package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics

/**
 * Wraps a still-Material destination (Search, Downloads) in NextTheme so leaving primary tabs does
 * not flip into a blue TopAppBar island. Like every secondary screen it draws no bottom chrome: its
 * large title says where you are. [where] names the pane for accessibility services.
 */
@Composable
fun NextChromeHost(
    where: String,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    NextTheme {
        val bottom = NO_RAIL_CLEARANCE + bottomInset()
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(next.background)
                    .semantics { paneTitle = where },
        ) {
            content(PaddingValues(bottom = bottom))
        }
    }
}
