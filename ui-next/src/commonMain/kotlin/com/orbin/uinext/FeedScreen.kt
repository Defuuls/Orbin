package com.orbin.uinext

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
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_downloads_tab
import com.orbin.uinext.resources.next_feed_title
import com.orbin.uinext.resources.next_search_title
import com.orbin.uinext.resources.reader_nav_boards
import com.orbin.uinext.resources.reader_reload
import com.orbin.uinext.resources.reader_view_grid
import com.orbin.uinext.resources.reader_view_list
import com.orbin.uinext.resources.tiktok_feed_following_empty
import com.orbin.uinext.resources.tiktok_feed_following_hint
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import com.orbin.uinext.tokens.ReaderType
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource

/** Identifies the production Home reader, including its first-load and empty states. */
object ReaderFeedTags {
    const val ROOT = "reader-feed-root"
    const val GRID = "reader-feed-grid"
    const val FOLLOWING = "reader-feed-following"
    const val FOR_YOU = "reader-feed-for-you"

    fun row(id: String): String = "reader-feed-row:$id"

    fun thumbnail(id: String): String = "reader-feed-thumbnail:$id"

    fun more(id: String): String = "reader-feed-more:$id"
}

/** Legacy identifiers remain source compatible; Home no longer renders these immersive nodes. */
object TikTokFeedTags {
    const val PAGER = "tiktok_feed_pager"
    const val HEADER = "tiktok_feed_header"
    const val CAPTION = "tiktok_feed_caption"
    const val ACTIONS = "tiktok_feed_actions"
    const val FOLLOWING = "tiktok_feed_following"
    const val FOR_YOU = "tiktok_feed_for_you"

    fun page(id: String): String = "tiktok_feed_page:$id"
}

/**
 * Home uses the same list rows and grid cards as board catalogs. Source and feed membership
 * belong to the hosts; only the presentation changes. Media starts in its explicit viewer,
 * while the bounded Home thumbnails remain static in every theme and catalog mode.
 */
@Suppress("UNUSED_PARAMETER")
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
    groupByBoard: Boolean = false,
    columns: Int? = null,
    uniformCards: Boolean = false,
    onOpenSearch: (() -> Unit)? = null,
    onOpenSaved: (() -> Unit)? = null,
    onOpenWatched: (() -> Unit)? = null,
    onToggleWatch: ((FeedRow) -> Unit)? = null,
    onToggleSaved: ((FeedRow) -> Unit)? = null,
    onShareRow: ((FeedRow) -> Unit)? = null,
    sourceLabels: List<String> = emptyList(),
    selectedSource: Int = 0,
    onSelectSource: (Int) -> Unit = {},
    statusMessage: String? = null,
    mediaContent: (@Composable (FeedRow, Modifier, Boolean) -> Unit)? = null,
    onOpenBoard: ((FeedRow) -> Unit)? = null,
    onUpcomingPreviewChanged: (String?) -> Unit = {},
    following: Boolean? = null,
    onFollowingChange: (Boolean) -> Unit = {},
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    onLoadMore: (() -> Unit)? = null,
    onOpenReplies: ((FeedRow) -> Unit)? = null,
    repliesOpenComments: Boolean = onOpenReplies == null,
    loadMoreKey: Any? = null,
    mode: CatalogMode = CatalogMode.LIST,
    onModeChange: ((CatalogMode) -> Unit)? = null,
    wideWindow: Boolean = false,
    onOpenMedia: ((FeedRow) -> Unit)? = null,
) {
    val previewCallback by rememberUpdatedState(onActivePreviewChanged)
    val upcomingCallback by rememberUpdatedState(onUpcomingPreviewChanged)
    val chromeCallback by rememberUpdatedState(onChromeVisibleChange)
    val titleCallback by rememberUpdatedState(onCompactTitleVisibleChange)
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
    val destinations = readerFeedDestinations(onSettings, onOpenBoards, onOpenSearch, onOpenSaved, onOpenWatched)
    val onDestination: ((NextDestination) -> Unit)? =
        if (destinations.size > 1) {
            { destination ->
                when (destination) {
                    NextDestination.FEED -> Unit
                    NextDestination.BOARDS -> onOpenBoards?.invoke()
                    NextDestination.DOWNLOADS -> onOpenDownloads?.invoke()
                    NextDestination.SEARCH -> onOpenSearch?.invoke()
                    NextDestination.SAVED -> onOpenSaved?.invoke()
                    NextDestination.WATCHED -> onOpenWatched?.invoke()
                    NextDestination.SETTINGS -> onSettings?.invoke()
                }
            }
        } else {
            null
        }
    val staticThumbnail: (@Composable (FeedRow, Modifier) -> Unit)? =
        thumbnail ?: mediaContent?.let { content -> { row, imageModifier -> content(row, imageModifier, false) } }
    val modeLabel =
        stringResource(
            if (mode ==
                CatalogMode.LIST
            ) {
                Res.string.reader_view_grid
            } else {
                Res.string.reader_view_list
            },
        )
    val downloadsLabel = stringResource(Res.string.next_downloads_tab)
    val rowActions =
        ReaderFeedRowActions(
            onOpenRow,
            onOpenMedia,
            onOpenReplies,
            repliesOpenComments,
            onOpenBoard,
            onToggleWatch,
            onToggleSaved,
            onShareRow,
        )
    NextScaffold(
        where = stringResource(Res.string.next_feed_title),
        modifier = modifier.fillMaxSize(),
        destination = NextDestination.FEED.takeIf { showRail },
        onDestination = onDestination.takeIf { showRail },
        drawerDestinations = destinations,
        actions =
            listOfNotNull(
                onRetry?.let {
                    ReaderAction(ReaderIcons.Refresh, stringResource(Res.string.reader_reload), it)
                },
                onOpenSearch?.let {
                    ReaderAction(ReaderIcons.Search, stringResource(Res.string.next_search_title), it)
                },
            ),
        overflow =
            listOfNotNull(
                onModeChange?.let { change ->
                    ReaderMenuItem(modeLabel) {
                        change(
                            if (mode ==
                                CatalogMode.LIST
                            ) {
                                CatalogMode.GRID
                            } else {
                                CatalogMode.LIST
                            },
                        )
                    }
                },
                onOpenDownloads?.let { ReaderMenuItem(downloadsLabel, onClick = it) },
            ),
    ) { bottomPadding ->
        Column(Modifier.fillMaxSize().background(reader.background).testTag(ReaderFeedTags.ROOT)) {
            ReaderFeedControls(
                sourceLabels = sourceLabels,
                selectedSource = selectedSource,
                onSelectSource = onSelectSource,
                subtitle = subtitle,
                statusMessage = statusMessage,
                headerContent = headerContent,
            )
            if (query.isNotEmpty()) ReaderCatalogSearch(query, onQueryChange, { onQueryChange("") })
            ReaderRefreshProgress(refreshing = isLoading && rows.isNotEmpty())
            val positions = rememberSaveableStateHolder()
            Box(Modifier.weight(1f)) {
                positions.SaveableStateProvider("$selectedSource:subscriptions") {
                    ReaderFeedGrid(
                        rows = rows,
                        mode = mode,
                        wideWindow = wideWindow,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onRetry = onRetry,
                        onOpenBoards = onOpenBoards,
                        onLoadMore = onLoadMore,
                        loadMoreKey = loadMoreKey,
                        thumbnail = staticThumbnail,
                        activityText = activityText,
                        actions = rowActions,
                        bottomPadding = bottomPadding,
                    )
                }
            }
            if (rows.isNotEmpty() && errorMessage != null && !isLoading) {
                ReaderRefreshFailure(ReaderFailureCause.UNKNOWN, onRetry = onRetry, message = errorMessage)
            }
        }
    }
}

private fun readerFeedDestinations(
    onSettings: (() -> Unit)?,
    onOpenBoards: (() -> Unit)?,
    onOpenSearch: (() -> Unit)?,
    onOpenSaved: (() -> Unit)?,
    onOpenWatched: (() -> Unit)?,
): Set<NextDestination> =
    buildSet {
        add(NextDestination.FEED)
        if (onOpenBoards != null) add(NextDestination.BOARDS)
        if (onOpenWatched != null) add(NextDestination.WATCHED)
        if (onOpenSaved != null) add(NextDestination.SAVED)
        if (onOpenSearch != null) add(NextDestination.SEARCH)
        if (onSettings != null) add(NextDestination.SETTINGS)
    }

@Composable
private fun ReaderFeedGrid(
    rows: List<FeedRow>,
    mode: CatalogMode,
    wideWindow: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: (() -> Unit)?,
    onOpenBoards: (() -> Unit)?,
    onLoadMore: (() -> Unit)?,
    loadMoreKey: Any?,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)?,
    activityText: @Composable (FeedRow) -> String,
    actions: ReaderFeedRowActions,
    bottomPadding: PaddingValues,
) {
    val state = rememberReaderFeedState(rows)
    val loadMoreCallback by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state, rows.size, onLoadMore != null, loadMoreKey) {
        snapshotFlow {
            rows.isNotEmpty() &&
                state.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index
                    ?.let { it >= rows.lastIndex - 2 } == true
        }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) loadMoreCallback?.invoke() }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val grid = mode == CatalogMode.GRID
        val gap = if (grid) ReaderSpace.gridGap else 0.dp
        val outer = if (grid) ReaderSpace.gridOuter else 0.dp
        val stateHeight = (maxHeight - outer * 2 - bottomPadding.calculateBottomPadding()).coerceAtLeast(0.dp)
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (grid) catalogGridColumns(maxWidth) else 1),
            state = state,
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
                item(key = "reader-feed-state", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(stateHeight)) {
                        ReaderFeedEmpty(isLoading, errorMessage, onRetry, onOpenBoards, mode, wideWindow)
                    }
                }
            } else {
                items(count = rows.size, key = { rows[it].id }, contentType = { mode }) { index ->
                    val row = rows[index]
                    ReaderFeedItem(row, mode, wideWindow, thumbnail, activityText(row), actions)
                }
            }
        }
    }
}

/** Empty asynchronous source loads must not replace the last content anchor with item zero. */
@Composable
private fun rememberReaderFeedState(rows: List<FeedRow>): LazyGridState {
    val state = rememberLazyGridState()
    var anchorId by rememberSaveable { mutableStateOf<String?>(null) }
    var anchorIndex by rememberSaveable { mutableIntStateOf(0) }
    var anchorOffset by rememberSaveable { mutableIntStateOf(0) }
    var awaitingData by rememberSaveable { mutableStateOf(false) }
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
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: (() -> Unit)?,
    onOpenBoards: (() -> Unit)?,
    mode: CatalogMode,
    wideWindow: Boolean,
) {
    when {
        isLoading -> ReaderCatalogSkeleton(mode = mode, wideWindow = wideWindow)
        errorMessage != null -> ReaderErrorState(ReaderFailureCause.UNKNOWN, onRetry = onRetry, message = errorMessage)
        else ->
            Column(Modifier.fillMaxSize()) {
                ReaderEmptyState(
                    title = stringResource(Res.string.tiktok_feed_following_empty),
                    message = stringResource(Res.string.tiktok_feed_following_hint),
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
                            style = ReaderType.label.withReaderFont(),
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
