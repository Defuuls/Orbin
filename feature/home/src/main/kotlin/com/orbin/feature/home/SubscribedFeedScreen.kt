package com.orbin.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.MediaType
import com.orbin.core.model.mutedTagTokens
import com.orbin.core.ui.post.PostCommentText
import com.orbin.core.ui.state.ErrorView
import com.orbin.core.ui.state.LoadingView
import com.orbin.media.image.OrbinAsyncImage
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

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
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior =
        if (showTopBar && chromeHidesOnScroll) {
            TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
        } else {
            null
        }

    LaunchedEffect(chromeHidesOnScroll, listState) {
        if (!chromeHidesOnScroll) {
            onChromeVisibleChange(true)
            return@LaunchedEffect
        }

        var previous = listState.scrollPositionKey()
        snapshotFlow { listState.scrollPositionKey() }
            .collect { current ->
                val scrollingUp = current < previous
                onChromeVisibleChange(current == 0 || scrollingUp)
                previous = current
            }
    }

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) {
            listState.animateScrollToItem(0)
        }
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
                TopAppBar(
                    modifier =
                        Modifier.clickable(
                            onClickLabel = "Scroll to top",
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        ),
                    title = {
                        Text("Subscribed")
                    },
                    actions = {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh feed")
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
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                            onSetBoardThreadLimit = viewModel::setBoardThreadLimit,
                            onOpenThread = onOpenThread,
                            listState = listState,
                            showBoardHeaders = showBoardHeaders,
                            tabletLayout = tabletFeedLayout,
                        )
                    }
            }
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
    onSetBoardThreadLimit: (BoardId, FeedThreadLimit?) -> Unit,
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    listState: LazyListState,
    showBoardHeaders: Boolean,
    tabletLayout: Boolean,
) {
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
            // In full-screen mode the pinned board headers are dropped entirely so nothing
            // stays fixed at the top and the threads flow as one uninterrupted list.
            if (showBoardHeaders) {
                stickyHeader(key = "header-${feed.board.id.value}") {
                    BoardFeedHeader(
                        feed = feed,
                        globalThreadLimit = globalThreadLimit,
                        onSetThreadLimit = { limit -> onSetBoardThreadLimit(feed.board.id, limit) },
                    )
                }
            }
            items(feed.threads, key = { "${feed.board.id.value}-${it.key.thread.value}" }) { thread ->
                FeedThreadCell(
                    thread = thread,
                    mutedTags = mutedTags,
                    thumbnailSizeDp = thumbnailSizeDp,
                    tabletLayout = tabletLayout,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "/${feed.board.id.value}/ - ${feed.board.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = if (tabletLayout) 8.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(if (tabletLayout) 12.dp else 10.dp),
    ) {
        FeedThumbnail(thread = thread, modifier = Modifier.size(thumbnailSizeDp))
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
                AssistChip(onClick = onClick, label = { Text("${thread.stats.replyCount} replies") })
                AssistChip(onClick = onClick, label = { Text("${thread.stats.imageCount} media") })
                if (isMuted) {
                    AssistChip(onClick = onClick, label = { Text("Muted") })
                }
            }
            Box(modifier = Modifier.heightIn(max = if (tabletLayout) 72.dp else 64.dp)) {
                PostCommentText(
                    comment = thread.originalPost.comment,
                    onQuoteClick = { onClick() },
                    onLinkClick = { onClick() },
                    onClick = onClick,
                )
            }
        }
    }
}

@Composable
private fun FeedThumbnail(
    thread: CatalogThread,
    modifier: Modifier = Modifier,
) {
    val attachment = thread.originalPost.attachments.firstOrNull()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier,
    ) {
        if (attachment != null) {
            OrbinAsyncImage(
                url = attachment.thumbnailUrl,
                contentDescription = attachment.originalFileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (attachment.type == MediaType.VIDEO) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("VID", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("OP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

private fun LazyListState.scrollPositionKey(): Int =
    firstVisibleItemIndex * SCROLL_POSITION_INDEX_WEIGHT + firstVisibleItemScrollOffset

private const val SCROLL_POSITION_INDEX_WEIGHT = 100_000

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
