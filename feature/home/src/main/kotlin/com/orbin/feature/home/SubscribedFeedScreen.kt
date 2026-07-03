package com.orbin.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaType
import com.orbin.core.model.mutedTagTokens
import com.orbin.core.ui.post.PostCommentText
import com.orbin.core.ui.state.ErrorView
import com.orbin.core.ui.state.LoadingView
import com.orbin.media.image.OrbinAsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscribedFeedScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    onOpenBoards: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: SubscribedFeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
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
            )
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
                            providerId = viewModel.providerId,
                            feeds = state.boards,
                            mutedTags = settings.mutedTagTokens(),
                            thumbnailSizeDp = settings.thumbnailSize.sizeDp.dp,
                            onOpenThread = onOpenThread,
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
    mutedTags: Set<String>,
    thumbnailSizeDp: androidx.compose.ui.unit.Dp,
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        feeds.forEach { feed ->
            stickyHeader(key = "header-${feed.board.id.value}") {
                BoardFeedHeader(feed)
            }
            items(feed.threads, key = { "${feed.board.id.value}-${it.key.thread.value}" }) { thread ->
                FeedThreadCell(
                    thread = thread,
                    mutedTags = mutedTags,
                    thumbnailSizeDp = thumbnailSizeDp,
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
private fun BoardFeedHeader(feed: SubscribedBoardFeed) {
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
            AssistChip(onClick = {}, label = { Text("${feed.threads.size} threads") })
        }
    }
}

@Composable
private fun FeedThreadCell(
    thread: CatalogThread,
    mutedTags: Set<String>,
    thumbnailSizeDp: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val isMuted = thread.matchesAny(mutedTags)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().alpha(if (isMuted) 0.62f else 1f).clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FeedThumbnail(thread = thread, modifier = Modifier.size(thumbnailSizeDp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = thread.originalPost.subject ?: "No.${thread.key.thread.value}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = {}, label = { Text("${thread.stats.replyCount} replies") })
                    AssistChip(onClick = {}, label = { Text("${thread.stats.imageCount} media") })
                    if (isMuted) {
                        AssistChip(onClick = {}, label = { Text("Muted") })
                    }
                }
                Box(modifier = Modifier.heightIn(max = 64.dp)) {
                    PostCommentText(comment = thread.originalPost.comment)
                }
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
