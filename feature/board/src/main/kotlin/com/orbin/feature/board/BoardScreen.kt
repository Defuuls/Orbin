package com.orbin.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaType
import com.orbin.core.ui.date.formatThreadDate
import com.orbin.core.ui.post.PostCommentPreviewText
import com.orbin.media.image.OrbinAsyncImage
import kotlinx.coroutines.launch

/** Board catalog with a Kuroba-inspired dense list/grid presentation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    onBack: () -> Unit,
    viewModel: BoardViewModel = hiltViewModel(),
) {
    val threads = viewModel.catalog.collectAsLazyPagingItems()
    val watchedThreadIds by viewModel.watchedThreadIds.collectAsStateWithLifecycle()
    var layoutMode by rememberSaveable { androidx.compose.runtime.mutableStateOf(BoardLayoutMode.List) }

    val boardKey = "${viewModel.providerId}/${viewModel.boardId}"
    val listState = rememberSaveable(boardKey, saver = LazyListState.Saver) { LazyListState() }
    val gridState = rememberSaveable(boardKey, saver = LazyGridState.Saver) { LazyGridState() }
    val scope = rememberCoroutineScope()

    val openThread: (CatalogThread) -> Unit = { thread ->
        onOpenThread(
            viewModel.providerId,
            viewModel.boardId,
            thread.key.thread.value,
            thread.originalPost.subject ?: "/${viewModel.boardId}/",
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                modifier =
                    Modifier.clickable(
                        onClickLabel = "Scroll to top",
                        onClick = {
                            scope.launch {
                                when (layoutMode) {
                                    BoardLayoutMode.List -> listState.animateScrollToItem(0)
                                    BoardLayoutMode.Grid -> gridState.animateScrollToItem(0)
                                }
                            }
                        },
                    ),
                title = {
                    Column {
                        Text(viewModel.title.ifBlank { "/${viewModel.boardId}/" })
                        Text(
                            text = "Catalog",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            layoutMode =
                                if (layoutMode == BoardLayoutMode.List) {
                                    BoardLayoutMode.Grid
                                } else {
                                    BoardLayoutMode.List
                                }
                        },
                    ) {
                        Icon(
                            imageVector =
                                if (layoutMode == BoardLayoutMode.List) {
                                    Icons.Filled.GridView
                                } else {
                                    Icons.Filled.ViewAgenda
                                },
                            contentDescription =
                                if (layoutMode == BoardLayoutMode.List) {
                                    "Show grid catalog"
                                } else {
                                    "Show list catalog"
                                },
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { padding ->
        when (layoutMode) {
            BoardLayoutMode.List ->
                CatalogList(
                    contentPadding = padding,
                    itemCount = threads.itemCount,
                    itemKey = { index -> threads[index]?.key?.thread?.value ?: index },
                    threadAt = { threads[it] },
                    watchedThreadIds = watchedThreadIds,
                    onToggleSubscription = viewModel::toggleThreadSubscription,
                    onOpenThread = openThread,
                    listState = listState,
                )

            BoardLayoutMode.Grid ->
                CatalogGrid(
                    contentPadding = padding,
                    itemCount = threads.itemCount,
                    itemKey = { index -> threads[index]?.key?.thread?.value ?: index },
                    threadAt = { threads[it] },
                    watchedThreadIds = watchedThreadIds,
                    onToggleSubscription = viewModel::toggleThreadSubscription,
                    onOpenThread = openThread,
                    gridState = gridState,
                )
        }
    }
}

@Composable
private fun CatalogList(
    contentPadding: PaddingValues,
    itemCount: Int,
    itemKey: (Int) -> Any,
    threadAt: (Int) -> CatalogThread?,
    watchedThreadIds: Set<Long>,
    onToggleSubscription: (CatalogThread) -> Unit,
    onOpenThread: (CatalogThread) -> Unit,
    listState: LazyListState,
) {
    val layoutDirection = LocalLayoutDirection.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        state = listState,
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + 8.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                end = contentPadding.calculateEndPadding(layoutDirection) + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 8.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count = itemCount, key = itemKey) { index ->
            val thread = threadAt(index) ?: return@items
            KurobaListThreadCell(
                thread = thread,
                isSubscribed = thread.key.thread.value in watchedThreadIds,
                onToggleSubscription = { onToggleSubscription(thread) },
                onClick = { onOpenThread(thread) },
            )
        }
    }
}

@Composable
private fun CatalogGrid(
    contentPadding: PaddingValues,
    itemCount: Int,
    itemKey: (Int) -> Any,
    threadAt: (Int) -> CatalogThread?,
    watchedThreadIds: Set<Long>,
    onToggleSubscription: (CatalogThread) -> Unit,
    onOpenThread: (CatalogThread) -> Unit,
    gridState: LazyGridState,
) {
    val layoutDirection = LocalLayoutDirection.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(168.dp),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        state = gridState,
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + 8.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                end = contentPadding.calculateEndPadding(layoutDirection) + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 8.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count = itemCount, key = itemKey) { index ->
            val thread = threadAt(index) ?: return@items
            KurobaGridThreadCell(
                thread = thread,
                isSubscribed = thread.key.thread.value in watchedThreadIds,
                onToggleSubscription = { onToggleSubscription(thread) },
                onClick = { onOpenThread(thread) },
            )
        }
    }
}

@Composable
private fun KurobaListThreadCell(
    thread: CatalogThread,
    isSubscribed: Boolean,
    onToggleSubscription: () -> Unit,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CatalogThumbnail(thread = thread, modifier = Modifier.size(112.dp))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        ThreadTitle(thread = thread, modifier = Modifier.weight(1f))
                        WatchButton(isSubscribed = isSubscribed, onClick = onToggleSubscription)
                    }

                    MetadataRow(thread = thread, compact = false, onClick = onClick)

                    Box(modifier = Modifier.heightIn(max = 76.dp)) {
                        PostCommentPreviewText(comment = thread.originalPost.comment)
                    }
                }
            }

            if (thread.previewReplies.isNotEmpty()) {
                PreviewReplyStrip(thread, onClick = onClick)
            }
        }
    }
}

@Composable
private fun KurobaGridThreadCell(
    thread: CatalogThread,
    isSubscribed: Boolean,
    onToggleSubscription: () -> Unit,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box {
                CatalogThumbnail(
                    thread = thread,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.15f),
                )
                FilledTonalIconButton(
                    onClick = onToggleSubscription,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription =
                            if (isSubscribed) {
                                "Unsubscribe from thread"
                            } else {
                                "Subscribe to thread"
                            },
                        tint =
                            if (isSubscribed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ThreadTitle(thread = thread, maxLines = 2)
                MetadataRow(thread = thread, compact = true, onClick = onClick)
                Box(modifier = Modifier.heightIn(max = 72.dp)) {
                    PostCommentPreviewText(comment = thread.originalPost.comment)
                }
            }
        }
    }
}

@Composable
private fun CatalogThumbnail(
    thread: CatalogThread,
    modifier: Modifier = Modifier,
) {
    val attachment = thread.originalPost.attachments.firstOrNull()
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (attachment == null) {
            Text(
                text = "No image",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            OrbinAsyncImage(
                url = attachment.thumbnailUrl,
                contentDescription = attachment.originalFileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            if (attachment.isSpoiler) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)))
                Icon(Icons.Filled.VisibilityOff, contentDescription = "Spoiler", tint = Color.White)
            } else if (attachment.type == MediaType.VIDEO || attachment.type == MediaType.AUDIO) {
                Surface(color = Color.Black.copy(alpha = 0.62f), shape = RoundedCornerShape(999.dp)) {
                    Text(
                        text = if (attachment.type == MediaType.AUDIO) "AUDIO" else "VIDEO",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadTitle(
    thread: CatalogThread,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thread.stats.isSticky) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = "Sticky",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = thread.originalPost.subject ?: "No.${thread.key.thread.value}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MetadataRow(
    thread: CatalogThread,
    compact: Boolean,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        formatThreadDate(thread.originalPost.createdAtMillis)?.let { created ->
            StatChip(created, onClick)
        }
        StatChip("${thread.stats.replyCount} replies", onClick)
        StatChip("${thread.stats.imageCount} media", onClick)
        if (!compact && thread.stats.uniquePosterCount > 0) {
            StatChip("${thread.stats.uniquePosterCount} posters", onClick)
        }
        if (thread.stats.isClosed) {
            StatChip("closed", onClick)
        }
        if (thread.stats.isArchived) {
            StatChip("archived", onClick)
        }
    }
}

@Composable
private fun StatChip(
    text: String,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(text, maxLines = 1) },
        modifier = Modifier.heightIn(min = 28.dp),
    )
}

@Composable
private fun WatchButton(
    isSubscribed: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = Icons.Outlined.Notifications,
            contentDescription =
                if (isSubscribed) {
                    "Unsubscribe from thread"
                } else {
                    "Subscribe to thread"
                },
            tint =
                if (isSubscribed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@Composable
private fun PreviewReplyStrip(
    thread: CatalogThread,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        thread.previewReplies.take(2).forEach { reply ->
            Surface(
                onClick = onClick,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "No.${reply.id.value}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Box(modifier = Modifier.weight(1f).heightIn(max = 44.dp)) {
                        PostCommentPreviewText(comment = reply.comment)
                    }
                }
            }
        }
    }
}

private enum class BoardLayoutMode {
    List,
    Grid,
}
