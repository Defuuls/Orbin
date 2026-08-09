package com.orbin.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.AppIconVariant
import com.orbin.core.model.AppSettings
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.ThumbnailSize
import com.orbin.core.model.mutedTagTokens
import com.orbin.core.ui.date.formatPostDateTime
import com.orbin.core.ui.post.PostCommentPreviewText
import com.orbin.core.ui.state.ErrorView
import com.orbin.core.ui.state.LoadingView
import com.orbin.media.image.MediaThumbnail
import com.orbin.media.image.OrbinAsyncImage
import com.orbin.media.video.VideoPlayer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.filled.Image as ImageIcon
import com.orbin.core.designsystem.R as DesignSystemR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscribedFeedScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    onOpenBoards: () -> Unit,
    onOpenSettings: () -> Unit,
    chromeHidesOnScroll: Boolean = false,
    showTopBar: Boolean = true,
    showBoardHeaders: Boolean = true,
    tabletFeedLayout: Boolean = false,
    scrollToTopRequest: Int = 0,
    refreshRequest: Int = 0,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    viewModel: SubscribedFeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val providerId by viewModel.providerId.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var collapsedBoards by rememberSaveable { mutableStateOf(setOf<String>()) }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    var layoutMode by rememberSaveable { mutableStateOf(FeedLayoutMode.List) }
    var thumbnailSizeOverride by rememberSaveable { mutableStateOf<ThumbnailSize?>(null) }
    val thumbnailSize = thumbnailSizeOverride ?: settings.thumbnailSize
    val scope = rememberCoroutineScope()
    val scrollBehavior =
        if (showTopBar && chromeHidesOnScroll) {
            TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
        } else {
            null
        }

    LaunchedEffect(chromeHidesOnScroll, layoutMode, listState, gridState) {
        if (!chromeHidesOnScroll) {
            onChromeVisibleChange(true)
            return@LaunchedEffect
        }

        val positionKeyFlow = feedScrollPositionKeyFlow(layoutMode, listState, gridState)
        var previous = feedScrollPositionKey(layoutMode, listState, gridState)
        positionKeyFlow.collect { current ->
            val scrollingUp = current < previous
            onChromeVisibleChange(current == 0 || scrollingUp)
            previous = current
        }
    }

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) scrollFeedToTop(layoutMode, listState, gridState)
    }

    LaunchedEffect(refreshRequest) {
        if (refreshRequest > 0) {
            viewModel.refresh()
        }
    }

    Scaffold(
        modifier =
            if (scrollBehavior != null) {
                Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
            } else {
                Modifier
            },
        // Keep only the top inset (used when the top bar is hidden): the app scaffold's bottom
        // bar covers the bottom inset, and in full-screen mode the feed should reach the edge.
        contentWindowInsets =
            ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            if (showTopBar) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    TopAppBar(
                        modifier =
                            Modifier.clickable(
                                onClickLabel = "Scroll to top",
                                onClick = { scope.launch { scrollFeedToTop(layoutMode, listState, gridState) } },
                            ),
                        title = {
                            SubscribedFeedTopBarTitle(
                                settings = settings,
                                providerId = providerId,
                            )
                        },
                        actions = {
                            IconButton(onClick = viewModel::refresh) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh feed")
                            }
                            IconButton(
                                onClick = { collapsedBoards = emptySet() },
                                enabled = collapsedBoards.isNotEmpty(),
                            ) {
                                Icon(Icons.Filled.ExpandLess, contentDescription = "Expand all boards")
                            }
                            IconButton(
                                onClick = {
                                    val allBoardIds =
                                        (uiState as? SubscribedFeedUiState.Success)
                                            ?.boards
                                            ?.map {
                                                it.board.id.value
                                            }?.toSet()
                                            ?: emptySet()
                                    collapsedBoards = allBoardIds
                                },
                                enabled =
                                    run {
                                        val allBoardIds =
                                            (uiState as? SubscribedFeedUiState.Success)
                                                ?.boards
                                                ?.map {
                                                    it.board.id.value
                                                }?.toSet()
                                                ?: emptySet()
                                        collapsedBoards.size < allBoardIds.size
                                    },
                            ) {
                                Icon(Icons.Filled.ExpandMore, contentDescription = "Collapse all boards")
                            }
                            if (layoutMode == FeedLayoutMode.ThumbnailGrid) {
                                IconButton(onClick = { thumbnailSizeOverride = thumbnailSize.next() }) {
                                    Icon(
                                        Icons.Filled.PhotoSizeSelectLarge,
                                        contentDescription = "Thumbnail size: ${thumbnailSize.label}",
                                    )
                                }
                            }
                            IconButton(onClick = { layoutMode = layoutMode.next() }) {
                                Icon(
                                    imageVector =
                                        when (layoutMode) {
                                            FeedLayoutMode.List -> Icons.Filled.GridView
                                            FeedLayoutMode.Grid -> Icons.Filled.ImageIcon
                                            FeedLayoutMode.ThumbnailGrid -> Icons.Filled.ViewAgenda
                                        },
                                    contentDescription =
                                        when (layoutMode) {
                                            FeedLayoutMode.List -> "Show grid feed"
                                            FeedLayoutMode.Grid -> "Show image-only feed"
                                            FeedLayoutMode.ThumbnailGrid -> "Show list feed"
                                        },
                                )
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings")
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                scrolledContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                        scrollBehavior = scrollBehavior,
                    )
                    // Overlaid on the whole bar rather than centered within the title slot, so it
                    // sits in the true middle of the top bar regardless of how wide the branding
                    // (start) or action icons (end) end up being.
                    LockNowButton(
                        visible = settings.biometricLockEnabled,
                        modifier = Modifier.align(Alignment.Center),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            viewModel.lockNow()
                        },
                    )
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                viewModel.refresh()
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val state = uiState) {
                SubscribedFeedUiState.Loading -> LoadingView()
                is SubscribedFeedUiState.Error -> ErrorView(state.message, onRetry = viewModel::refresh)
                is SubscribedFeedUiState.Success ->
                    if (state.boards.isEmpty()) {
                        EmptySubscribedFeed(onOpenBoards = onOpenBoards, onOpenSettings = onOpenSettings)
                    } else {
                        SubscribedFeedList(
                            providerId = providerId,
                            feeds = state.boards,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            // Parse once per raw value: a fresh Set each recomposition would
                            // defeat Compose skipping for the whole feed subtree.
                            mutedTags = remember(settings.mutedTags) { settings.mutedTagTokens() },
                            thumbnailSizeDp = settings.thumbnailSize.sizeDp.dp,
                            globalThreadLimit = settings.feedThreadLimit,
                            mediaScrollEnabled = settings.mediaScrollBoardView,
                            autoplayVideosInFeed = settings.autoplayVideosInFeed,
                            muteByDefault = settings.muteByDefault,
                            onSetBoardThreadLimit = viewModel::setBoardThreadLimit,
                            onOpenThread = onOpenThread,
                            listState = listState,
                            gridState = gridState,
                            layoutMode = layoutMode,
                            gridThumbnailSize = thumbnailSize,
                            showBoardHeaders = showBoardHeaders,
                            tabletLayout = tabletFeedLayout,
                            collapsedBoards = collapsedBoards,
                            onCollapsedBoardsChange = { collapsedBoards = it },
                        )
                    }
            }
        }
    }
}

@Composable
private fun SubscribedFeedTopBarTitle(
    settings: AppSettings,
    providerId: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp),
        ) {
            Image(
                painter = painterResource(settings.appIconVariant.drawableRes()),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(2.dp),
            )
        }
        Column {
            Text(
                text = "Orbin",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = providerId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = settings.appIconVariant.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Failsafe: instantly covers the app and demands re-authentication, without waiting for a
 * background/foreground cycle. Only shown when there is actually a lock to trigger.
 */
@Composable
private fun LockNowButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (visible) {
        IconButton(onClick = onClick, modifier = modifier) {
            Icon(Icons.Filled.Lock, contentDescription = "Lock Orbin now")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubscribedFeedList(
    providerId: String,
    feeds: List<SubscribedBoardFeed>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    mutedTags: Set<String>,
    thumbnailSizeDp: Dp,
    globalThreadLimit: FeedThreadLimit,
    mediaScrollEnabled: Boolean,
    autoplayVideosInFeed: Boolean,
    muteByDefault: Boolean,
    onSetBoardThreadLimit: (BoardId, FeedThreadLimit?) -> Unit,
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    listState: LazyListState,
    gridState: LazyGridState,
    layoutMode: FeedLayoutMode,
    gridThumbnailSize: ThumbnailSize,
    showBoardHeaders: Boolean,
    tabletLayout: Boolean,
    collapsedBoards: Set<String>,
    onCollapsedBoardsChange: (Set<String>) -> Unit,
) {
    if (layoutMode != FeedLayoutMode.List) {
        SubscribedFeedGrid(
            providerId = providerId,
            feeds = feeds,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            layoutMode = layoutMode,
            thumbnailSize = gridThumbnailSize,
            globalThreadLimit = globalThreadLimit,
            autoplayVideosInFeed = autoplayVideosInFeed,
            muteByDefault = muteByDefault,
            onSetBoardThreadLimit = onSetBoardThreadLimit,
            onOpenThread = onOpenThread,
            gridState = gridState,
            collapsedBoards = collapsedBoards,
            onCollapsedBoardsChange = onCollapsedBoardsChange,
        )
        return
    }

    val visibleKeys by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.mapTo(mutableSetOf()) { it.key } }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding =
            if (tabletLayout) {
                PaddingValues(horizontal = 18.dp, vertical = 10.dp)
            } else {
                PaddingValues(8.dp)
            },
        verticalArrangement = Arrangement.spacedBy(if (tabletLayout) 5.dp else 8.dp),
    ) {
        item(key = "subscribed-search") {
            SubscribedFeedSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
            )
        }

        val filteredFeeds = feeds.filterBySearchQuery(searchQuery)
        if (filteredFeeds.isEmpty() && searchQuery.isNotBlank()) {
            item(key = "subscribed-search-empty") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "No subscribed threads match your search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        filteredFeeds.forEach { feed ->
            val isBoardCollapsed = collapsedBoards.contains(feed.board.id.value)
            // In full-screen mode the pinned board headers are dropped entirely so nothing
            // stays fixed at the top and the threads flow as one uninterrupted list.
            // However, always show the header for collapsed boards so they can be expanded.
            if (showBoardHeaders || isBoardCollapsed) {
                stickyHeader(key = "header-${feed.board.id.value}") {
                    BoardFeedHeader(
                        feed = feed,
                        globalThreadLimit = globalThreadLimit,
                        isCollapsed = isBoardCollapsed,
                        onToggleCollapse =
                            { boardId ->
                                onCollapsedBoardsChange(
                                    if (collapsedBoards.contains(boardId)) {
                                        collapsedBoards - boardId
                                    } else {
                                        collapsedBoards + boardId
                                    },
                                )
                            },
                        onSetThreadLimit = { limit -> onSetBoardThreadLimit(feed.board.id, limit) },
                    )
                }
            }
            if (!isBoardCollapsed) {
                items(feed.threads, key = { "${feed.board.id.value}-${it.key.thread.value}" }) { thread ->
                    val itemKey = "${feed.board.id.value}-${thread.key.thread.value}"
                    FeedThreadCell(
                        thread = thread,
                        mutedTags = mutedTags,
                        thumbnailSizeDp = thumbnailSizeDp,
                        tabletLayout = tabletLayout,
                        mediaScrollEnabled = mediaScrollEnabled,
                        autoplayVideo = autoplayVideosInFeed && itemKey in visibleKeys,
                        muted = muteByDefault,
                        onClick = {
                            onOpenThread(
                                providerId,
                                feed.board.id.value,
                                thread.key.thread.value,
                                thread.originalPost.subject ?: "/${feed.board.id.value}/",
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Grid/image-only presentation for the subscribed feed, mirroring the board catalog's own
 * List/Grid/image-only split. Board headers are full-width items rather than sticky ones — Compose
 * grids don't support pinned headers the way [LazyColumn.stickyHeader] does — but collapse/expand
 * and per-board thread limits work exactly as they do in list mode.
 */
@Composable
private fun SubscribedFeedGrid(
    providerId: String,
    feeds: List<SubscribedBoardFeed>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    layoutMode: FeedLayoutMode,
    thumbnailSize: ThumbnailSize,
    globalThreadLimit: FeedThreadLimit,
    autoplayVideosInFeed: Boolean,
    muteByDefault: Boolean,
    onSetBoardThreadLimit: (BoardId, FeedThreadLimit?) -> Unit,
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    gridState: LazyGridState,
    collapsedBoards: Set<String>,
    onCollapsedBoardsChange: (Set<String>) -> Unit,
) {
    val fill = layoutMode == FeedLayoutMode.ThumbnailGrid && thumbnailSize == ThumbnailSize.FILL
    val columns =
        when {
            fill -> GridCells.Fixed(1)
            layoutMode == FeedLayoutMode.ThumbnailGrid -> GridCells.Adaptive(thumbnailSize.sizeDp.dp)
            else -> GridCells.Adaptive(168.dp)
        }
    val visibleKeys by remember(gridState) {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.mapTo(mutableSetOf()) { it.key } }
    }

    LazyVerticalGrid(
        columns = columns,
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (layoutMode == FeedLayoutMode.ThumbnailGrid) 4.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (layoutMode == FeedLayoutMode.ThumbnailGrid) 4.dp else 8.dp),
    ) {
        item(key = "subscribed-search", span = { GridItemSpan(maxLineSpan) }) {
            SubscribedFeedSearchBar(query = searchQuery, onQueryChange = onSearchQueryChange)
        }

        val filteredFeeds = feeds.filterBySearchQuery(searchQuery)
        if (filteredFeeds.isEmpty() && searchQuery.isNotBlank()) {
            item(key = "subscribed-search-empty", span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "No subscribed threads match your search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        filteredFeeds.forEach { feed ->
            val isBoardCollapsed = collapsedBoards.contains(feed.board.id.value)
            item(key = "header-${feed.board.id.value}", span = { GridItemSpan(maxLineSpan) }) {
                BoardFeedHeader(
                    feed = feed,
                    globalThreadLimit = globalThreadLimit,
                    isCollapsed = isBoardCollapsed,
                    onToggleCollapse =
                        { boardId ->
                            onCollapsedBoardsChange(
                                if (collapsedBoards.contains(boardId)) {
                                    collapsedBoards - boardId
                                } else {
                                    collapsedBoards + boardId
                                },
                            )
                        },
                    onSetThreadLimit = { limit -> onSetBoardThreadLimit(feed.board.id, limit) },
                )
            }
            if (!isBoardCollapsed) {
                gridItems(feed.threads, key = { "${feed.board.id.value}-${it.key.thread.value}" }) { thread ->
                    val itemKey = "${feed.board.id.value}-${thread.key.thread.value}"
                    val onClick = {
                        onOpenThread(
                            providerId,
                            feed.board.id.value,
                            thread.key.thread.value,
                            thread.originalPost.subject ?: "/${feed.board.id.value}/",
                        )
                    }
                    if (layoutMode == FeedLayoutMode.ThumbnailGrid) {
                        FeedThumbnailOnlyCell(
                            thread = thread,
                            onClick = onClick,
                            fullResolution = fill || thumbnailSize == ThumbnailSize.LARGE,
                            modifier =
                                if (fill) {
                                    Modifier.fillMaxWidth().aspectRatio(1f)
                                } else {
                                    Modifier.size(thumbnailSize.sizeDp.dp)
                                },
                        )
                    } else {
                        FeedGridThreadCell(
                            thread = thread,
                            onClick = onClick,
                            autoplayVideo = autoplayVideosInFeed && itemKey in visibleKeys,
                            muted = muteByDefault,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedGridThreadCell(
    thread: CatalogThread,
    onClick: () -> Unit,
    autoplayVideo: Boolean = false,
    muted: Boolean = true,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            FeedThumbnail(
                thread = thread,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.15f),
                autoplayVideo = autoplayVideo,
                muted = muted,
            )
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = thread.originalPost.subject ?: "No.${thread.key.thread.value}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "/${thread.key.board.value}/ · ${thread.stats.replyCount} replies",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FeedThumbnailOnlyCell(
    thread: CatalogThread,
    onClick: () -> Unit,
    fullResolution: Boolean,
    modifier: Modifier = Modifier,
) {
    val attachment = thread.originalPost.attachments.firstOrNull()
    Box(modifier = modifier.clip(RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
        if (attachment == null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "OP",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            MediaThumbnail(
                attachment = attachment,
                modifier = Modifier.fillMaxSize(),
                fullResolution = fullResolution,
                onClick = onClick,
            )
        }
    }
}

@Composable
private fun SubscribedFeedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Search subscribed threads") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon =
            if (query.isNotBlank()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            } else {
                null
            },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
    )
}

@Composable
private fun BoardFeedHeader(
    feed: SubscribedBoardFeed,
    globalThreadLimit: FeedThreadLimit,
    isCollapsed: Boolean,
    onToggleCollapse: (String) -> Unit,
    onSetThreadLimit: (FeedThreadLimit?) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier =
                    Modifier.weight(1f).clickable {
                        onToggleCollapse(feed.board.id.value)
                    },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (isCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                        contentDescription = if (isCollapsed) "Expand board" else "Collapse board",
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = "/${feed.board.id.value}/ - ${feed.board.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (feed.board.description.isNotBlank()) {
                    Text(
                        text = feed.board.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                AssistChip(
                    onClick = { menuExpanded = true },
                    label = { Text("${feed.threads.size} threads") },
                    trailingIcon = {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Adjust threads shown for this board")
                    },
                )
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Default (${globalThreadLimit.label})") },
                        onClick = {
                            onSetThreadLimit(null)
                            menuExpanded = false
                        },
                        leadingIcon = selectedIconOrNull(feed.threadLimitOverride == null),
                    )
                    FeedThreadLimit.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onSetThreadLimit(option)
                                menuExpanded = false
                            },
                            leadingIcon = selectedIconOrNull(feed.threadLimitOverride == option),
                        )
                    }
                }
            }
        }
    }
}

private fun selectedIconOrNull(selected: Boolean): (@Composable () -> Unit)? =
    if (selected) {
        { Icon(Icons.Filled.Check, contentDescription = null) }
    } else {
        null
    }

@Composable
private fun FeedThreadCell(
    thread: CatalogThread,
    mutedTags: Set<String>,
    thumbnailSizeDp: Dp,
    tabletLayout: Boolean,
    mediaScrollEnabled: Boolean = false,
    autoplayVideo: Boolean = false,
    muted: Boolean = true,
    onClick: () -> Unit,
) {
    val isMuted = thread.matchesAny(mutedTags)

    if (tabletLayout) {
        Surface(
            modifier = Modifier.fillMaxWidth().alpha(if (isMuted) 0.62f else 1f).clickable(onClick = onClick),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            FeedThreadCellContent(
                thread = thread,
                isMuted = isMuted,
                thumbnailSizeDp = 108.dp,
                tabletLayout = true,
                mediaScrollEnabled = mediaScrollEnabled,
                autoplayVideo = autoplayVideo,
                muted = muted,
                onClick = onClick,
            )
        }
    } else {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().alpha(if (isMuted) 0.62f else 1f).clickable(onClick = onClick),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            FeedThreadCellContent(
                thread = thread,
                isMuted = isMuted,
                thumbnailSizeDp = thumbnailSizeDp,
                tabletLayout = false,
                mediaScrollEnabled = mediaScrollEnabled,
                autoplayVideo = autoplayVideo,
                muted = muted,
                onClick = onClick,
            )
        }
    }
}

@Composable
private fun FeedThreadCellContent(
    thread: CatalogThread,
    isMuted: Boolean,
    thumbnailSizeDp: Dp,
    tabletLayout: Boolean,
    mediaScrollEnabled: Boolean = false,
    autoplayVideo: Boolean = false,
    muted: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = if (tabletLayout) 8.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(if (tabletLayout) 12.dp else 10.dp),
    ) {
        FeedThumbnail(
            thread = thread,
            modifier = Modifier.size(thumbnailSizeDp),
            mediaScrollEnabled = mediaScrollEnabled,
            autoplayVideo = autoplayVideo,
            muted = muted,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (tabletLayout) 4.dp else 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = thread.originalPost.subject ?: "No.${thread.key.thread.value}",
                    style =
                        if (tabletLayout) {
                            MaterialTheme.typography.bodyLarge
                        } else {
                            MaterialTheme.typography.titleSmall
                        },
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = "/${thread.key.board.value}/",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                formatPostDateTime(thread.originalPost.createdAtMillis)?.let { created ->
                    AssistChip(onClick = onClick, label = { Text(created) })
                }
                AssistChip(onClick = onClick, label = { Text("${thread.stats.replyCount} replies") })
                AssistChip(onClick = onClick, label = { Text("${thread.stats.imageCount} media") })
                if (isMuted) {
                    AssistChip(onClick = onClick, label = { Text("Muted") })
                }
            }
            Box(modifier = Modifier.heightIn(max = if (tabletLayout) 72.dp else 64.dp)) {
                PostCommentPreviewText(comment = thread.originalPost.comment)
            }
        }
    }
}

@Composable
private fun FeedThumbnail(
    thread: CatalogThread,
    modifier: Modifier = Modifier,
    mediaScrollEnabled: Boolean = false,
    autoplayVideo: Boolean = false,
    muted: Boolean = true,
) {
    val attachments = thread.originalPost.attachments
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier,
    ) {
        if (attachments.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("OP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        } else if (mediaScrollEnabled && attachments.size > 1) {
            val pagerState =
                androidx.compose.foundation.pager
                    .rememberPagerState(pageCount = { attachments.size })
            Box(modifier = Modifier.fillMaxSize()) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    // Only the thread's first attachment autoplays; later pages stay static
                    // thumbnails until the thread is opened.
                    FeedAttachmentPreview(
                        attachment = attachments[page],
                        autoplayVideo = page == 0 && autoplayVideo,
                        muted = muted,
                    )
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                ) {
                    Text(
                        "${pagerState.settledPage + 1} / ${attachments.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        } else {
            FeedAttachmentPreview(
                attachment = attachments.first(),
                autoplayVideo = autoplayVideo,
                muted = muted,
            )
        }
    }
}

/**
 * A single attachment preview: an actively-playing muted [VideoPlayer] when [autoplayVideo] is
 * set on a video attachment, otherwise the usual static thumbnail with a play-icon overlay for
 * video/audio. [autoplayVideo] is only ever true while the row is on screen — scrolling it away
 * flips this back to the static branch, which disposes the player rather than merely pausing it.
 */
@Composable
private fun FeedAttachmentPreview(
    attachment: MediaAttachment,
    autoplayVideo: Boolean,
    muted: Boolean,
) {
    if (autoplayVideo && attachment.type == MediaType.VIDEO) {
        VideoPlayer(
            url = attachment.sourceUrl,
            modifier = Modifier.fillMaxSize(),
            autoPlay = true,
            muted = muted,
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        OrbinAsyncImage(
            url = attachment.thumbnailUrl,
            contentDescription = attachment.originalFileName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (attachment.type == MediaType.VIDEO || attachment.type == MediaType.AUDIO) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(color = Color.Black.copy(alpha = 0.62f), shape = RoundedCornerShape(999.dp)) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = if (attachment.type == MediaType.AUDIO) "Audio" else "Video",
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp).size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySubscribedFeed(
    onOpenBoards: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No subscribed boards", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            text = "Subscribe to boards from the board gallery or run setup again.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onOpenBoards) { Text("Boards") }
            OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
        }
    }
}

private fun CatalogThread.matchesAny(tokens: Set<String>): Boolean {
    if (tokens.isEmpty()) return false
    val haystack = listOfNotNull(originalPost.subject, originalPost.comment).joinToString(" ").lowercase()
    return tokens.any(haystack::contains)
}

private fun AppIconVariant.drawableRes(): Int =
    when (this) {
        AppIconVariant.DEFAULT -> DesignSystemR.drawable.ic_launcher_orbital_orb
        AppIconVariant.NESTED_RINGS -> DesignSystemR.drawable.ic_launcher_nested_rings
        AppIconVariant.ABSTRACT_FLOW -> DesignSystemR.drawable.ic_launcher_abstract_flow
        AppIconVariant.MINIMALIST_ESSENCE -> DesignSystemR.drawable.ic_launcher_minimalist_essence
        AppIconVariant.DUAL_GRADIENT -> DesignSystemR.drawable.ic_launcher_dual_gradient
    }

private enum class FeedLayoutMode {
    List,
    Grid,
    ThumbnailGrid,
}

private fun FeedLayoutMode.next(): FeedLayoutMode {
    val values = FeedLayoutMode.entries
    return values[(values.indexOf(this) + 1) % values.size]
}

private fun ThumbnailSize.next(): ThumbnailSize {
    val values = ThumbnailSize.entries
    return values[(values.indexOf(this) + 1) % values.size]
}

private fun LazyListState.scrollPositionKey(): Int =
    firstVisibleItemIndex * SCROLL_POSITION_INDEX_WEIGHT + firstVisibleItemScrollOffset

private fun LazyGridState.scrollPositionKey(): Int =
    firstVisibleItemIndex * SCROLL_POSITION_INDEX_WEIGHT + firstVisibleItemScrollOffset

private const val SCROLL_POSITION_INDEX_WEIGHT = 100_000

private suspend fun scrollFeedToTop(
    layoutMode: FeedLayoutMode,
    listState: LazyListState,
    gridState: LazyGridState,
) {
    if (layoutMode == FeedLayoutMode.List) listState.animateScrollToItem(0) else gridState.animateScrollToItem(0)
}

private fun feedScrollPositionKey(
    layoutMode: FeedLayoutMode,
    listState: LazyListState,
    gridState: LazyGridState,
): Int = if (layoutMode == FeedLayoutMode.List) listState.scrollPositionKey() else gridState.scrollPositionKey()

private fun feedScrollPositionKeyFlow(
    layoutMode: FeedLayoutMode,
    listState: LazyListState,
    gridState: LazyGridState,
): Flow<Int> =
    if (layoutMode == FeedLayoutMode.List) {
        snapshotFlow { listState.scrollPositionKey() }
    } else {
        snapshotFlow { gridState.scrollPositionKey() }
    }

private fun List<SubscribedBoardFeed>.filterBySearchQuery(query: String): List<SubscribedBoardFeed> {
    val token = query.trim().lowercase()
    if (token.isBlank()) return this
    return mapNotNull { feed ->
        val threads = feed.threads.filter { thread -> thread.matchesSearch(token, feed.board.id.value) }
        if (threads.isEmpty()) {
            null
        } else {
            feed.copy(threads = threads.toImmutableList())
        }
    }
}

private fun CatalogThread.matchesSearch(
    query: String,
    board: String,
): Boolean {
    val haystack =
        listOfNotNull(
            board,
            originalPost.subject,
            originalPost.comment.raw,
            originalPost.poster.name,
            originalPost.poster.tripcode,
            originalPost.attachments.firstOrNull()?.originalFileName,
        ).joinToString(" ")
            .lowercase()
    return haystack.contains(query)
}
