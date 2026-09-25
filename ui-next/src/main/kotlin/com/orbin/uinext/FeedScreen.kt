package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun FeedScreen(
    rows: List<FeedRow>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showRail: Boolean = true,
    onOpenRow: (FeedRow) -> Unit = {},
    onSettings: (() -> Unit)? = null,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)? = null,
    activityText: @Composable (FeedRow) -> String = { it.activity },
    onActivePreviewChanged: (String?) -> Unit = {},
    hideRailOnScroll: Boolean = true,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    onCompactTitleVisibleChange: (Boolean) -> Unit = {},
    onOpenBoards: (() -> Unit)? = null,
    onOpenMedia: (() -> Unit)? = null,
    headerContent: @Composable () -> Unit = {},
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    groupByBoard: Boolean = true,
) {
    val gridState = rememberLazyGridState()

    val railVisible =
        if (!hideRailOnScroll) {
            true
        } else {
            scrollingUp({ gridState.firstVisibleItemIndex }, { gridState.firstVisibleItemScrollOffset })
        }
    LaunchedEffect(railVisible) { onChromeVisibleChange(railVisible) }
    val activePreviewCallback = rememberUpdatedState(onActivePreviewChanged)

    // Emit at most one on-screen preview id so callers can hard-cap feed ExoPlayers to 0–1.
    LaunchedEffect(rows, gridState) {
        snapshotFlow {
            val candidates =
                gridState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                    rows.firstOrNull { it.id == item.key && it.hasPreview && !it.muted }?.id
                }
            candidates.take(MAX_FEED_AUTOPLAY_IDS).firstOrNull()
        }.distinctUntilChanged()
            .collect { activePreviewCallback.value(it) }
    }

    DisposableEffect(Unit) {
        onDispose { activePreviewCallback.value(null) }
    }

    val hasTabs = onOpenBoards != null || onOpenMedia != null
    val onDestination: ((NextDestination) -> Unit)? =
        if (hasTabs) {
            { dest ->
                when (dest) {
                    NextDestination.FEED -> Unit
                    NextDestination.BOARDS -> onOpenBoards?.invoke()
                    NextDestination.MEDIA -> onOpenMedia?.invoke()
                    NextDestination.SETTINGS -> Unit
                }
            }
        } else {
            null
        }
    val feedTitle = stringResource(R.string.next_feed_title)
    val showCompactTitle by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 ||
                gridState.firstVisibleItemScrollOffset > 64
        }
    }
    LaunchedEffect(showCompactTitle) { onCompactTitleVisibleChange(showCompactTitle) }

    Box(modifier = modifier.fillMaxSize()) {
        NextScaffold(
            where = feedTitle.takeIf { showRail && !hasTabs },
            modifier = Modifier.fillMaxSize(),
            railVisible = railVisible,
            destination = NextDestination.FEED.takeIf { showRail && hasTabs },
            onDestination = onDestination.takeIf { showRail },
        ) { bottomPad ->
            val header: @Composable () -> Unit = {
                FeedHeader(
                    subtitle = subtitle ?: pluralStringResource(R.plurals.next_feed_thread_count, rows.size, rows.size),
                    headerContent = headerContent,
                    query = query,
                    onQueryChange = onQueryChange,
                    onSettings = onSettings,
                )
            }
            val insets = Modifier.fillMaxSize().contentInsets()
            val visibleRows = rows
            val groups =
                if (groupByBoard) {
                    visibleRows.groupBy { it.boardTitle to it.board }
                } else {
                    linkedMapOf(
                        ("" to "") to visibleRows,
                    )
                }
            LazyVerticalGrid(
                // Two columns on a phone, more on wider screens: the same grid the catalog uses.
                columns = GridCells.Adaptive(GRID_MIN_CELL),
                state = gridState,
                modifier = insets,
                contentPadding = gridPadding(bottomPad),
            ) {
                fullWidthItem { header() }
                if (visibleRows.isEmpty()) {
                    item(key = "feed-empty", span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(vertical = 24.dp)) {
                            ScreenTitle(
                                "Nothing here yet",
                                subtitle = "Try a different search, or follow a few more boards.",
                                size = 22,
                            )
                            onOpenBoards?.let { InlineAction("Explore boards", accent = true, onClick = it) }
                        }
                    }
                }
                groups.forEach { (board, group) ->
                    if (groupByBoard) {
                        item(key = "board:${board.second}", span = { GridItemSpan(maxLineSpan) }) {
                            FeedGroupHeading(board.first, board.second, group.size)
                        }
                    }
                    itemsIndexed(group, key = {
                        _,
                        row,
                        ->
                        row.id
                    }, contentType = { _, _ -> "feed-grid" }) { index, row ->
                        FeedGridCell(
                            row,
                            index,
                            onOpenRow,
                            thumbnail,
                            activityText = activityText,
                            showBoard = !groupByBoard,
                        )
                    }
                }
            }
        }
    }
}

internal const val FEED_SIZE_MIN_DP = MEDIA_SIZE_MIN_DP
internal const val FEED_SIZE_MAX_DP = MEDIA_SIZE_MAX_DP
internal const val FEED_SIZE_STEPS = MEDIA_SIZE_STEPS

/** Matches media.video.MAX_FEED_AUTOPLAY_PLAYERS — keep feed ExoPlayer count at 0 or 1. */
private const val MAX_FEED_AUTOPLAY_IDS = 1
