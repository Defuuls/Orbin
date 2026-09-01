package com.orbin.minimal

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.orbin.feature.home.NextFeedScreen

/** Minimal-owned join for the shared feed: its single secondary destination is Boards. */
@Composable
fun MinimalFeedScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    onOpenBoards: () -> Unit,
) {
    NextFeedScreen(
        onOpenThread = onOpenThread,
        onOpenCommands = onOpenBoards,
        railAction = stringResource(R.string.minimal_boards_title),
    )
}
