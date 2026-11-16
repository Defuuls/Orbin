package com.orbin.uinext.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.orbin.uinext.components.FeedRow
import com.orbin.uinext.components.GRID_SIDE_INSET
import com.orbin.uinext.components.LocalOrbinNavigationAtSide
import com.orbin.uinext.components.MEDIA_SIZE_MAX_DP
import com.orbin.uinext.components.MEDIA_SIZE_MIN_DP
import com.orbin.uinext.components.MEDIA_SIZE_STEPS
import com.orbin.uinext.components.NextDestination
import com.orbin.uinext.components.NextScaffold
import com.orbin.uinext.components.OrbinBottomBar
import com.orbin.uinext.components.ReaderAction
import com.orbin.uinext.components.ReaderCatalogSearch
import com.orbin.uinext.components.ReaderIcons
import com.orbin.uinext.components.ReaderMenuItem
import com.orbin.uinext.components.reader
import com.orbin.uinext.components.withReaderFont
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.feed_redesign_empty_hint
import com.orbin.uinext.resources.feed_redesign_empty_title
import com.orbin.uinext.resources.next_downloads_tab
import com.orbin.uinext.resources.next_feed_title
import com.orbin.uinext.resources.next_search_title
import com.orbin.uinext.resources.reader_nav_boards
import com.orbin.uinext.resources.reader_reload
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderSize
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource

/** Identifies the production Home reader, including its first-load and empty states. */
object ReaderFeedTags {
    const val ROOT = "reader-feed-root"
    const val GRID = "reader-feed-grid"
    const val FOLLOWING = "reader-feed-following"
    const val FOR_YOU = "reader-feed-for-you"
    const val MODE = "reader-feed-mode"
    const val SOURCE = "reader-feed-source"

    fun row(id: String): String = "reader-feed-row:$id"

    fun thumbnail(id: String): String = "reader-feed-thumbnail:$id"

    fun more(id: String): String = "reader-feed-more:$id"
}

/** Legacy identifiers remain source compatible; Home no longer renders these immersive nodes. */
object FeedPagerTags {
    const val PAGER = "feed_pager_pager"
    const val HEADER = "feed_pager_header"
    const val CAPTION = "feed_pager_caption"
    const val ACTIONS = "feed_pager_actions"
    const val FOLLOWING = "reader_feed_following"
    const val FOR_YOU = "reader_feed_for_you"

    fun page(id: String): String = "feed_pager_page:$id"
}

/**
 * Home uses the same list rows and cards as board catalogs. Source and feed membership
 * belong to the hosts; only the presentation changes. Media starts in its explicit viewer,
 * while the bounded Home thumbnails remain static in every theme and catalog mode.
 */
@Composable
fun FeedScreen(
    state: FeedScreenState,
    actions: FeedScreenActions,
    modifier: Modifier = Modifier,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)? = null,
    mediaContent: (@Composable (FeedRow, Modifier, Boolean) -> Unit)? = null,
    activityText: @Composable (FeedRow) -> String = { it.activity },
    headerContent: @Composable () -> Unit = {},
) {
    val previewCallback by rememberUpdatedState(actions.onActivePreviewChanged)
    val upcomingCallback by rememberUpdatedState(actions.onUpcomingPreviewChanged)
    val chromeCallback by rememberUpdatedState(actions.onChromeVisibleChange)
    val titleCallback by rememberUpdatedState(actions.onCompactTitleVisibleChange)
    DisposableEffect(Unit) {
        previewCallback(null)
        upcomingCallback(null)
        chromeCallback(true)
        titleCallback(false)
        onDispose {
            previewCallback(null)
            upcomingCallback(null)
        }
    }
    val onDestination = feedDestinationHandler(actions)
    val staticThumbnail: (@Composable (FeedRow, Modifier) -> Unit)? =
        thumbnail ?: mediaContent?.let { content -> { row, imageModifier -> content(row, imageModifier, false) } }
    val bottomDestinations = feedBottomDestinations(actions)
    val showBottomBar =
        state.showRail && onDestination != null && bottomDestinations.size > 1 && !LocalOrbinNavigationAtSide.current
    val downloadsLabel = stringResource(Res.string.next_downloads_tab)
    val rowActions =
        remember(actions, state.repliesOpenComments) {
            ReaderFeedRowActions(
                actions.onOpenRow,
                actions.onOpenMedia,
                actions.onOpenReplies,
                state.repliesOpenComments ?: (actions.onOpenReplies == null),
                actions.onOpenBoard,
                actions.onToggleWatch,
                actions.onToggleSaved,
                actions.onShareRow,
            )
        }
    NextScaffold(
        where = stringResource(Res.string.next_feed_title),
        modifier = modifier.fillMaxSize(),
        destination = NextDestination.FEED.takeIf { state.showRail },
        onDestination = onDestination.takeIf { state.showRail },
        actions =
            listOfNotNull(
                actions.onRetry?.let {
                    ReaderAction(ReaderIcons.Refresh, stringResource(Res.string.reader_reload), it)
                },
                actions.onOpenSearch?.let {
                    ReaderAction(ReaderIcons.Search, stringResource(Res.string.next_search_title), it)
                },
            ),
        overflow =
            buildList {
                actions.onModeChange?.let { change ->
                    addAll(catalogModeMenuItems(state.mode, change))
                }
                actions.onOpenDownloads?.let { add(ReaderMenuItem(downloadsLabel, onClick = it)) }
            },
    ) { bottomPadding ->
        Column(Modifier.fillMaxSize().background(reader.background).testTag(ReaderFeedTags.ROOT)) {
            ReaderFeedControls(
                sourceLabels = state.sourceLabels,
                selectedSource = state.selectedSource,
                onSelectSource = actions.onSelectSource,
                subtitle = state.subtitle,
                statusMessage = state.statusMessage,
                headerContent = headerContent,
                mode = state.mode,
                onModeChange = actions.onModeChange,
            )
            if (state.query.isNotEmpty()) {
                ReaderCatalogSearch(state.query, actions.onQueryChange, { actions.onQueryChange("") })
            }
            ReaderRefreshProgress(refreshing = state.isLoading && state.rows.isNotEmpty())
            val positions = rememberSaveableStateHolder()
            Box(Modifier.weight(1f)) {
                positions.SaveableStateProvider("${state.selectedSource}:subscriptions") {
                    ReaderFeedGrid(
                        state = state,
                        actions = actions,
                        thumbnail = staticThumbnail,
                        activityText = activityText,
                        rowActions = rowActions,
                        bottomPadding = if (showBottomBar) PaddingValues() else bottomPadding,
                    )
                }
            }
            if (state.rows.isNotEmpty() && state.errorMessage != null && !state.isLoading) {
                ReaderRefreshFailure(
                    ReaderFailureCause.UNKNOWN,
                    onRetry = actions.onRetry,
                    message = state.errorMessage,
                )
            }
            if (showBottomBar) {
                OrbinBottomBar(
                    selected = NextDestination.FEED,
                    onSelect = checkNotNull(onDestination),
                    destinations = bottomDestinations,
                    downloadBadge = state.downloadBadge,
                    watchedBadge = state.watchedBadge,
                )
            }
        }
    }
}

/** Routes a navigation destination to the host action that opens it; `null` when Home has none. */
internal fun feedDestinationHandler(actions: FeedScreenActions): ((NextDestination) -> Unit)? {
    if (actions.onOpenBoards == null && actions.onOpenLibrary == null && actions.onSettings == null) return null
    return { destination ->
        when (destination) {
            NextDestination.FEED -> Unit
            NextDestination.BOARDS -> actions.onOpenBoards?.invoke()
            NextDestination.DOWNLOADS -> actions.onOpenDownloads?.invoke()
            NextDestination.SEARCH -> actions.onOpenSearch?.invoke()
            NextDestination.SAVED -> actions.onOpenSaved?.invoke()
            NextDestination.WATCHED -> actions.onOpenWatched?.invoke()
            NextDestination.SETTINGS -> actions.onSettings?.invoke()
            NextDestination.LIBRARY -> actions.onOpenLibrary?.invoke()
        }
    }
}

/** The bottom bar shows Home plus each top-level destination the host can open, in spec order. */
internal fun feedBottomDestinations(actions: FeedScreenActions): List<NextDestination> =
    buildList {
        if (actions.onOpenBoards != null) add(NextDestination.BOARDS)
        add(NextDestination.FEED)
        if (actions.onOpenLibrary != null) add(NextDestination.LIBRARY)
        if (actions.onSettings != null) add(NextDestination.SETTINGS)
    }

/** Content types for the Home and catalog grids, so Compose reuses a composition only for a matching cell. */
internal enum class FeedItemType { STATE, LIST_ROW, GRID_CARD, FULL_CARD }

internal fun feedItemType(mode: CatalogMode): FeedItemType =
    when (mode) {
        CatalogMode.LIST -> FeedItemType.LIST_ROW
        CatalogMode.GRID -> FeedItemType.GRID_CARD
        CatalogMode.CARD -> FeedItemType.FULL_CARD
    }

@Composable
private fun ReaderFeedGrid(
    state: FeedScreenState,
    actions: FeedScreenActions,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)?,
    activityText: @Composable (FeedRow) -> String,
    rowActions: ReaderFeedRowActions,
    bottomPadding: PaddingValues,
) {
    val rows = state.rows
    val mode = state.mode
    val onLoadMore = actions.onLoadMore
    val gridState = rememberReaderFeedState(rows, mode)
    val loadMoreCallback by rememberUpdatedState(onLoadMore)
    LaunchedEffect(gridState, rows.size, onLoadMore != null, state.loadMoreKey) {
        snapshotFlow {
            rows.isNotEmpty() &&
                gridState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index
                    ?.let { it >= rows.lastIndex - 2 } == true
        }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) loadMoreCallback?.invoke() }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val grid = mode == CatalogMode.GRID
        val gap = ORBIN_CATALOG_GAP
        // First-load skeletons own the same gutters as the loaded cells.
        val outer = if (rows.isEmpty() && state.isLoading) 0.dp else GRID_SIDE_INSET
        val gridSpec =
            if (mode == CatalogMode.CARD) {
                orbinCatalogCardSpec(maxWidth, gutter = GRID_SIDE_INSET)
            } else {
                orbinCatalogGridSpec(maxWidth, gutter = GRID_SIDE_INSET)
            }
        val stateHeight = (maxHeight - outer * 2 - bottomPadding.calculateBottomPadding()).coerceAtLeast(0.dp)
        val itemType = feedItemType(mode)
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (grid) gridSpec.columns else 1),
            state = gridState,
            modifier = Modifier.fillMaxSize().testTag(ReaderFeedTags.GRID),
            contentPadding =
                PaddingValues(
                    start = outer,
                    top = outer,
                    end = outer,
                    bottom =
                        outer + bottomPadding.calculateBottomPadding(),
                ),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            if (rows.isEmpty()) {
                item(
                    key = "reader-feed-state",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = FeedItemType.STATE,
                ) {
                    Box(Modifier.fillMaxWidth().height(stateHeight)) {
                        ReaderFeedEmpty(state, actions)
                    }
                }
            } else {
                items(count = rows.size, key = { rows[it].id }, contentType = { itemType }) { index ->
                    val row = rows[index]
                    ReaderFeedItem(
                        row,
                        mode,
                        state.wideWindow,
                        thumbnail,
                        activityText(row),
                        rowActions,
                        gridSpec.imageHeight,
                    )
                }
            }
        }
    }
}

/** Empty asynchronous source loads must not replace the last content anchor with item zero. */
@Composable
private fun rememberReaderFeedState(
    rows: List<FeedRow>,
    mode: CatalogMode,
): LazyGridState {
    val state = rememberLazyGridState()
    var anchorId by rememberSaveable { mutableStateOf<String?>(null) }
    var anchorIndex by rememberSaveable { mutableIntStateOf(0) }
    var anchorOffset by rememberSaveable { mutableIntStateOf(0) }
    var awaitingData by rememberSaveable { mutableStateOf(false) }
    PreserveCatalogScrollAnchor(state, mode, onModeChange = { anchorOffset = 0 })
    // Each restored source/mode scope must resolve its saved identity against its current rows.
    // This flag is deliberately not saveable: hidden data may have changed before re-entry.
    var restoredThisMount by remember { mutableStateOf(false) }
    val ids = remember(rows) { rows.map { it.id } }
    val indices = remember(ids) { ids.withIndex().associate { it.value to it.index } }
    LaunchedEffect(state, ids) {
        if (ids.isEmpty()) {
            awaitingData = true
            return@LaunchedEffect
        }
        if (awaitingData || !restoredThisMount) {
            val savedIndex = if (anchorId == null) state.firstVisibleItemIndex else anchorIndex
            val savedOffset = if (anchorId == null) state.firstVisibleItemScrollOffset else anchorOffset
            val target = anchorId?.let(indices::get) ?: savedIndex.coerceIn(ids.indices)
            state.scrollToItem(target, savedOffset)
            awaitingData = false
            restoredThisMount = true
        }
        snapshotFlow {
            val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == state.firstVisibleItemIndex }
            val id = item?.key as? String
            val index = id?.let(indices::get)
            if (id != null && index != null) ReaderFeedPosition(id, index, state.firstVisibleItemScrollOffset) else null
        }.distinctUntilChanged().collect { position ->
            if (position != null) {
                anchorId = position.id
                anchorIndex = position.index
                anchorOffset = position.offset
            }
        }
    }
    return state
}

private data class ReaderFeedPosition(
    val id: String,
    val index: Int,
    val offset: Int,
)

@Composable
private fun ReaderFeedEmpty(
    state: FeedScreenState,
    actions: FeedScreenActions,
) {
    val onRetry = actions.onRetry
    val onOpenBoards = actions.onOpenBoards
    val mode = state.mode
    val errorMessage = state.errorMessage
    when {
        state.isLoading -> ReaderCatalogSkeleton(mode = mode, wideWindow = state.wideWindow, gutter = GRID_SIDE_INSET)
        errorMessage != null -> ReaderErrorState(ReaderFailureCause.UNKNOWN, onRetry = onRetry, message = errorMessage)
        else ->
            Column(Modifier.fillMaxSize()) {
                ReaderEmptyState(
                    title = state.emptyTitle ?: stringResource(Res.string.feed_redesign_empty_title),
                    message = state.emptyMessage ?: stringResource(Res.string.feed_redesign_empty_hint),
                    onReload = onRetry,
                    modifier = Modifier.weight(1f),
                )
                if (onOpenBoards != null) {
                    TextButton(
                        onClick = onOpenBoards,
                        modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = ReaderSize.touchTarget),
                    ) {
                        Text(
                            stringResource(Res.string.reader_nav_boards),
                            style = OrbinType.label.withReaderFont(),
                            color = reader.accent,
                        )
                    }
                }
            }
    }
}

/** Legacy density constants remain available to source-compatible integrations. */
internal val FEED_CARD_MIN_WIDTH = 360.dp
internal const val FEED_EXCERPT_LINES = 3
internal const val FEED_SIZE_MIN_DP = MEDIA_SIZE_MIN_DP
internal const val FEED_SIZE_MAX_DP = MEDIA_SIZE_MAX_DP
internal const val FEED_SIZE_STEPS = MEDIA_SIZE_STEPS
