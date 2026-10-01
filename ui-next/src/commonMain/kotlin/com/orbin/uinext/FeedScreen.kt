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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_explore_boards
import com.orbin.uinext.resources.next_feed_thread_count
import com.orbin.uinext.resources.next_feed_title
import com.orbin.uinext.resources.next_nothing_here_yet
import com.orbin.uinext.resources.next_thread_jump_top
import com.orbin.uinext.resources.next_try_different_search
import com.orbin.uinext.tokens.NextSpace
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
    onOpenDownloads: (() -> Unit)? = null,
    headerContent: @Composable () -> Unit = {},
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    groupByBoard: Boolean = true,
    // A fixed column count the caller chose for this device and window; null fits as many
    // full-width cards as the window allows.
    columns: Int? = null,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

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

    val hasTabs = onOpenBoards != null || onOpenDownloads != null
    val onDestination: ((NextDestination) -> Unit)? =
        if (hasTabs) {
            { dest ->
                when (dest) {
                    NextDestination.FEED -> Unit
                    NextDestination.BOARDS -> onOpenBoards?.invoke()
                    NextDestination.DOWNLOADS -> onOpenDownloads?.invoke()
                    NextDestination.SETTINGS -> Unit
                }
            }
        } else {
            null
        }
    val feedTitle = stringResource(Res.string.next_feed_title)
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
                    subtitle =
                        subtitle ?: pluralStringResource(Res.plurals.next_feed_thread_count, rows.size, rows.size),
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
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    // One full-width card per thread on a phone, its media whole and the opening post
                    // under it; wider screens fit more columns of the same card.
                    columns = columns?.let(GridCells::Fixed) ?: GridCells.Adaptive(FEED_CARD_MIN_WIDTH),
                    state = gridState,
                    modifier = insets,
                    contentPadding = gridPadding(bottomPad),
                ) {
                    fullWidthItem { header() }
                    if (visibleRows.isEmpty()) {
                        item(key = "feed-empty", span = { GridItemSpan(maxLineSpan) }) {
                            Column(Modifier.padding(vertical = 24.dp)) {
                                ScreenTitle(
                                    stringResource(Res.string.next_nothing_here_yet),
                                    subtitle = stringResource(Res.string.next_try_different_search),
                                    size = 22,
                                )
                                onOpenBoards?.let {
                                    InlineAction(
                                        stringResource(Res.string.next_explore_boards),
                                        accent = true,
                                        onClick = it,
                                    )
                                }
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
                                excerptLines = FEED_EXCERPT_LINES,
                                tallMedia = true,
                            )
                        }
                    }
                }
                if (rows.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            scope.launch { gridState.animateScrollToItem(0) }
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                        expanded = showCompactTitle,
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.ArrowUpward,
                                contentDescription = "Jump to top",
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(Res.string.next_thread_jump_top),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(
                                    end = NextSpace.gutter,
                                    bottom = bottomPad.calculateBottomPadding() + 16.dp,
                                ),
                    )
                }
            }
        }
    }
}

/** Wide enough that every phone gets one column; a tablet or unfolded foldable gets two or more. */
internal val FEED_CARD_MIN_WIDTH = 360.dp

/** Lines of the opening post under each feed card. */
internal const val FEED_EXCERPT_LINES = 8

internal const val FEED_SIZE_MIN_DP = MEDIA_SIZE_MIN_DP
internal const val FEED_SIZE_MAX_DP = MEDIA_SIZE_MAX_DP
internal const val FEED_SIZE_STEPS = MEDIA_SIZE_STEPS

/** Matches media.video.MAX_FEED_AUTOPLAY_PLAYERS — keep feed ExoPlayer count at 0 or 1. */
private const val MAX_FEED_AUTOPLAY_IDS = 1
