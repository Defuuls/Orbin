package com.orbin.feature.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThumbnailSize
import com.orbin.core.ui.date.formatRelativeTime
import com.orbin.core.ui.post.PostCommentText
import com.orbin.core.ui.state.ErrorView
import com.orbin.core.ui.state.LoadingView
import com.orbin.media.image.MediaThumbnail
import kotlinx.coroutines.launch

/**
 * Thread viewer. Renders the OP and replies as cards, with tappable quote links that scroll to
 * the referenced post, backlink chips, inline media thumbnails, and per-post collapsing for
 * skimming long threads. The post→index map makes quote navigation O(1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    onBack: () -> Unit,
    onOpenMedia: (Int) -> Unit,
    mediaScrollIndex: Int? = null,
    onMediaScrollConsumed: () -> Unit = {},
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()
    val mediaScrollEnabled by viewModel.mediaScrollEnabled.collectAsStateWithLifecycle()
    var layoutMode by rememberSaveable { mutableStateOf(ThreadLayoutMode.Posts) }
    val defaultThumbnailSize by viewModel.thumbnailSize.collectAsStateWithLifecycle()
    // Lets the grid toggle temporarily override the persisted default for this session, without
    // writing back to Settings.
    var thumbnailSizeOverride by rememberSaveable { mutableStateOf<ThumbnailSize?>(null) }
    val thumbnailSize = thumbnailSizeOverride ?: defaultThumbnailSize
    val snackbarHostState = remember { SnackbarHostState() }
    var scrollToTopRequest by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(exportMessage) {
        exportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeExportMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                modifier =
                    Modifier.clickable(
                        onClickLabel = "Scroll to top",
                        onClick = { scrollToTopRequest += 1 },
                    ),
                title = { Text(viewModel.title.ifBlank { "Thread" }, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            layoutMode =
                                if (layoutMode == ThreadLayoutMode.Posts) {
                                    ThreadLayoutMode.ThumbnailGrid
                                } else {
                                    ThreadLayoutMode.Posts
                                }
                        },
                    ) {
                        Icon(
                            imageVector =
                                if (layoutMode == ThreadLayoutMode.Posts) {
                                    Icons.Filled.GridView
                                } else {
                                    Icons.Filled.ViewAgenda
                                },
                            contentDescription =
                                if (layoutMode == ThreadLayoutMode.Posts) {
                                    "Show thumbnails only"
                                } else {
                                    "Show posts"
                                },
                        )
                    }
                    if (layoutMode == ThreadLayoutMode.ThumbnailGrid) {
                        IconButton(onClick = { thumbnailSizeOverride = thumbnailSize.next() }) {
                            Icon(
                                Icons.Filled.PhotoSizeSelectLarge,
                                contentDescription = "Thumbnail size: ${thumbnailSize.label}",
                            )
                        }
                    }
                    IconButton(onClick = viewModel::toggleBookmark) {
                        Icon(
                            imageVector =
                                if (isBookmarked) {
                                    Icons.Filled.Bookmark
                                } else {
                                    Icons.Outlined.BookmarkBorder
                                },
                            contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark",
                        )
                    }
                    ThreadOverflowMenu(
                        onDownloadAllMedia = viewModel::downloadAllMedia,
                        onExportLinks = viewModel::exportLinks,
                    )
                },
            )
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
                ThreadUiState.Loading -> LoadingView()
                is ThreadUiState.Error -> ErrorView(state.message, onRetry = viewModel::refresh)
                is ThreadUiState.Success ->
                    ThreadContent(
                        thread = state.thread,
                        layoutMode = layoutMode,
                        thumbnailSize = thumbnailSize,
                        onOpenMedia = onOpenMedia,
                        mediaScrollIndex = mediaScrollIndex,
                        onMediaScrollConsumed = onMediaScrollConsumed,
                        scrollToTopRequest = scrollToTopRequest,
                        mediaScrollEnabled = mediaScrollEnabled,
                        modifier = Modifier.fillMaxSize(),
                    )
            }
        }
    }
}

/**
 * Download and export are the two actions in the thread bar that aren't a glance-and-tap toggle —
 * both take a moment to run and neither is reached for on every visit. Folding them into one menu
 * keeps the bar from turning into a row of unlabeled icons a reader has to decode one at a time.
 */
@Composable
private fun ThreadOverflowMenu(
    onDownloadAllMedia: () -> Unit,
    onExportLinks: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More thread actions")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Download all media") },
                leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDownloadAllMedia()
                },
            )
            DropdownMenuItem(
                text = { Text("Export links") },
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                onClick = {
                    expanded = false
                    onExportLinks()
                },
            )
        }
    }
}

@Composable
private fun ThreadContent(
    thread: Thread,
    layoutMode: ThreadLayoutMode,
    thumbnailSize: ThumbnailSize,
    onOpenMedia: (Int) -> Unit,
    mediaScrollIndex: Int? = null,
    onMediaScrollConsumed: () -> Unit = {},
    scrollToTopRequest: Int,
    mediaScrollEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    when (layoutMode) {
        ThreadLayoutMode.Posts ->
            PostListContent(
                thread = thread,
                onOpenMedia = onOpenMedia,
                mediaScrollIndex = mediaScrollIndex,
                onMediaScrollConsumed = onMediaScrollConsumed,
                scrollToTopRequest = scrollToTopRequest,
                mediaScrollEnabled = mediaScrollEnabled,
                modifier = modifier,
            )
        ThreadLayoutMode.ThumbnailGrid ->
            ThumbnailGridContent(
                thread = thread,
                thumbnailSize = thumbnailSize,
                onOpenMedia = onOpenMedia,
                mediaScrollIndex = mediaScrollIndex,
                onMediaScrollConsumed = onMediaScrollConsumed,
                scrollToTopRequest = scrollToTopRequest,
                modifier = modifier,
            )
    }
}

@Composable
private fun PostListContent(
    thread: Thread,
    onOpenMedia: (Int) -> Unit,
    mediaScrollIndex: Int? = null,
    onMediaScrollConsumed: () -> Unit = {},
    scrollToTopRequest: Int,
    mediaScrollEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val posts = remember(thread.key) { thread.allPosts }
    val indexById =
        remember(thread.key) {
            posts.withIndex().associate { (index, post) -> post.id to index + 1 } // +1 for the header item
        }
    // Flattened media index across the whole thread, so a tapped attachment opens at the right page.
    val mediaIndexById =
        remember(thread.key) {
            posts.flatMap { it.attachments }.withIndex().associate { (index, media) -> media.id to index }
        }
    // Reverse lookup from the gallery page to the owning post row in this LazyColumn.
    val postIndexByMediaIndex =
        remember(thread.key) {
            buildMap {
                var mediaIndex = 0
                posts.forEachIndexed { postIndex, post ->
                    post.attachments.forEach { _ ->
                        put(mediaIndex, postIndex + 1) // +1 for the header item
                        mediaIndex += 1
                    }
                }
            }
        }
    // Collapsed post ids, persisted across configuration changes and keyed to the thread so
    // switching threads starts fresh. A list of Longs is trivially Saveable, unlike a map of PostId.
    val collapsedIds =
        rememberSaveable(
            thread.key,
            saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
        ) {
            mutableStateListOf<Long>()
        }
    val listState =
        rememberSaveable(thread.key, saver = LazyListState.Saver) {
            LazyListState()
        }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val haptics = LocalHapticFeedback.current

    val onQuoteClick: (PostId) -> Unit = { id ->
        indexById[id]?.let { target -> scope.launch { listState.animateScrollToItem(target) } }
    }
    val onLinkClick: (String) -> Unit = { url ->
        runCatching { uriHandler.openUri(url) }
    }

    LaunchedEffect(mediaScrollIndex, postIndexByMediaIndex) {
        val target = mediaScrollIndex?.let(postIndexByMediaIndex::get) ?: return@LaunchedEffect
        listState.animateScrollToItem(target)
        onMediaScrollConsumed()
    }

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "stats") { ThreadStatsHeader(thread) }

        items(count = posts.size, key = { posts[it].id.value }) { index ->
            val post = posts[index]
            PostCard(
                post = post,
                isCollapsed = post.id.value in collapsedIds,
                onToggleCollapse = {
                    val collapsing = !collapsedIds.remove(post.id.value)
                    if (collapsing) collapsedIds.add(post.id.value)
                    // Collapsing removes what the reader was looking at, so the tick confirms the
                    // tap landed on the header rather than on something inside the post.
                    haptics.performHapticFeedback(
                        if (collapsing) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                    )
                },
                onQuoteClick = onQuoteClick,
                onLinkClick = onLinkClick,
                onMediaClick = { mediaId -> mediaIndexById[mediaId]?.let(onOpenMedia) },
                mediaScrollEnabled = mediaScrollEnabled,
            )
        }
    }
}

@Composable
private fun ThumbnailGridContent(
    thread: Thread,
    thumbnailSize: ThumbnailSize,
    onOpenMedia: (Int) -> Unit,
    mediaScrollIndex: Int? = null,
    onMediaScrollConsumed: () -> Unit = {},
    scrollToTopRequest: Int,
    modifier: Modifier = Modifier,
) {
    val attachments = remember(thread.key) { thread.allPosts.flatMap { it.attachments } }
    val gridState =
        rememberSaveable(thread.key, saver = LazyGridState.Saver) {
            LazyGridState()
        }
    val fill = thumbnailSize == ThumbnailSize.FILL

    LaunchedEffect(mediaScrollIndex) {
        val target = mediaScrollIndex ?: return@LaunchedEffect
        gridState.animateScrollToItem(target)
        onMediaScrollConsumed()
    }

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) gridState.animateScrollToItem(0)
    }

    LazyVerticalGrid(
        columns = if (fill) GridCells.Fixed(1) else GridCells.Adaptive(thumbnailSize.sizeDp.dp),
        modifier = modifier,
        state = gridState,
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(count = attachments.size, key = { attachments[it].id }) { index ->
            MediaThumbnail(
                attachment = attachments[index],
                modifier =
                    if (fill) {
                        Modifier.fillMaxWidth().aspectRatio(1f)
                    } else {
                        Modifier.size(thumbnailSize.sizeDp.dp)
                    },
                // Provider thumbnails are ~250px, which upscales visibly at any tier once
                // display density passes ~2.6x — Medium (96dp) included — so always pull the
                // full-resolution source here; Coil downsamples the decode to the grid cell,
                // so this doesn't cost extra memory, only bandwidth.
                fullResolution = true,
                onClick = { onOpenMedia(index) },
            )
        }
    }
}

@Composable
private fun ThreadStatsHeader(thread: Thread) {
    val stats = thread.stats
    Text(
        text =
            buildString {
                append("${stats.replyCount} replies · ${stats.imageCount} images")
                if (stats.uniquePosterCount > 0) append(" · ${stats.uniquePosterCount} posters")
                if (stats.isClosed) append(" · closed")
                if (stats.isArchived) append(" · archived")
            },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostCard(
    post: Post,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onQuoteClick: (PostId) -> Unit,
    onLinkClick: (String) -> Unit,
    onMediaClick: (String) -> Unit,
    mediaScrollEnabled: Boolean = true,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            PostHeader(post = post, isCollapsed = isCollapsed, onClick = onToggleCollapse)

            if (!isCollapsed) {
                if (post.attachments.isNotEmpty()) {
                    Spacer(Modifier.padding(top = 8.dp))
                    if (mediaScrollEnabled) {
                        val pagerState = remember(post.id) { PagerState(pageCount = { post.attachments.size }) }
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                                val attachment = post.attachments[page]
                                MediaThumbnail(
                                    attachment = attachment,
                                    modifier = Modifier.fillMaxSize(),
                                    fullResolution = true,
                                    onClick = { onMediaClick(attachment.id) },
                                )
                            }

                            if (post.attachments.size > 1) {
                                Box(
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Black.copy(alpha = 0.62f))
                                            .padding(
                                                horizontal = 8.dp,
                                                vertical = 4.dp,
                                            ),
                                ) {
                                    Text(
                                        text = "${pagerState.settledPage + 1}/${post.attachments.size}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            MediaThumbnail(
                                attachment = post.attachments[0],
                                modifier = Modifier.fillMaxSize(),
                                fullResolution = true,
                                onClick = { onMediaClick(post.attachments[0].id) },
                            )
                        }
                    }
                }
                if (post.comment.nodes.isNotEmpty()) {
                    Spacer(Modifier.padding(top = 8.dp))
                    PostCommentText(
                        comment = post.comment,
                        selectable = true,
                        onQuoteClick = onQuoteClick,
                        onLinkClick = onLinkClick,
                    )
                }
                if (post.backlinks.isNotEmpty()) {
                    Spacer(Modifier.padding(top = 8.dp))
                    Backlinks(post.backlinks, onQuoteClick)
                }
            }
        }
    }
}

@Composable
private fun PostHeader(
    post: Post,
    isCollapsed: Boolean,
    onClick: () -> Unit,
) {
    val postedTime = remember(post.createdAtMillis) { formatRelativeTime(post.createdAtMillis) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = post.poster.name ?: "Anonymous",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        post.poster.posterId?.let {
            Text("ID:$it", style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        postedTime?.let { posted ->
            Text(
                text = posted,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            text = "No.${post.id.value}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        // A text "[+]" suffix reads as a hack; the same collapsed/expanded state a reader already
        // knows from the post body being hidden gets a real Material affordance here instead.
        Icon(
            imageVector = if (isCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
            contentDescription = if (isCollapsed) "Expand post" else "Collapse post",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Backlinks(
    backlinks: List<PostId>,
    onQuoteClick: (PostId) -> Unit,
) {
    HorizontalDivider(Modifier.padding(bottom = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        backlinks.forEach { id ->
            Text(
                text = ">>${id.value}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                // A bare clickable on labelSmall text is roughly a 16dp tap target, well under the
                // 48dp minimum. minimumInteractiveComponentSize expands the touch bounds without
                // inflating the glyphs, which is what keeps the dense catalog look intact; the
                // padding inside the clickable widens the hit area and gives the ripple a shape
                // to fill.
                modifier =
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClickLabel = "Jump to post ${id.value}") { onQuoteClick(id) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

private enum class ThreadLayoutMode {
    Posts,
    ThumbnailGrid,
}

private fun ThumbnailSize.next(): ThumbnailSize {
    val values = ThumbnailSize.entries
    return values[(values.indexOf(this) + 1) % values.size]
}
