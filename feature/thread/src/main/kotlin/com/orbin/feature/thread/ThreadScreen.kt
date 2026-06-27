package com.orbin.feature.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.Post
import com.orbin.core.model.PostId
import com.orbin.core.model.Thread
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
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.title.ifBlank { "Thread" }, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            ThreadUiState.Loading -> LoadingView(Modifier.padding(padding))
            is ThreadUiState.Error -> ErrorView(state.message, Modifier.padding(padding))
            is ThreadUiState.Success ->
                ThreadContent(
                    thread = state.thread,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
        }
    }
}

@Composable
private fun ThreadContent(
    thread: Thread,
    modifier: Modifier = Modifier,
) {
    val posts = remember(thread) { thread.allPosts }
    val indexById =
        remember(posts) {
            posts.withIndex().associate { (index, post) -> post.id to index + 1 } // +1 for the header item
        }
    val collapsed = remember { mutableStateMapOf<PostId, Boolean>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val onQuoteClick: (PostId) -> Unit = { id ->
        indexById[id]?.let { target -> scope.launch { listState.animateScrollToItem(target) } }
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
                isCollapsed = collapsed[post.id] == true,
                onToggleCollapse = { collapsed[post.id] = !(collapsed[post.id] ?: false) },
                onQuoteClick = onQuoteClick,
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
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            PostHeader(post = post, isCollapsed = isCollapsed, onClick = onToggleCollapse)

            if (!isCollapsed) {
                post.attachments.firstOrNull()?.let { media ->
                    Spacer(Modifier.padding(top = 8.dp))
                    MediaThumbnail(attachment = media)
                }
                if (post.comment.nodes.isNotEmpty()) {
                    Spacer(Modifier.padding(top = 8.dp))
                    PostCommentText(comment = post.comment, onQuoteClick = onQuoteClick)
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
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = post.poster.name ?: "Anonymous",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        post.poster.posterId?.let {
            Text("ID:$it", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "No.${post.id.value}" + if (isCollapsed) "  [+]" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        backlinks.forEach { id ->
            Text(
                text = ">>${id.value}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onQuoteClick(id) },
            )
        }
    }
}
