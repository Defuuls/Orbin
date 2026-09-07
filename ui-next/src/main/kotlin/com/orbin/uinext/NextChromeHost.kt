package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Wraps a still-Material destination in NextTheme + ContextRail so leaving Feed does not flip into
 * a blue TopAppBar island. Inner lists may keep Material widgets temporarily; the chrome matches.
 */
@Composable
fun NextChromeHost(
    where: String,
    onOpenCommands: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: String = stringResource(R.string.next_action_search),
    content: @Composable (PaddingValues) -> Unit,
) {
    NextTheme {
        val bottom = RAIL_HEIGHT + 28.dp + bottomInset()
        Box(modifier = modifier.fillMaxSize().background(next.background)) {
            content(PaddingValues(bottom = bottom))
            ContextRail(
                where = where,
                detail = detail,
                action = action,
                onSearch = onOpenCommands,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
