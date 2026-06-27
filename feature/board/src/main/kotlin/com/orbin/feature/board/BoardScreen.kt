package com.orbin.feature.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.orbin.core.model.CatalogThread
import com.orbin.core.ui.post.PostCommentText
import com.orbin.media.image.MediaThumbnail

/** Board catalog: a paged list of thread cards. Tapping a card opens the thread. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    onBack: () -> Unit,
    viewModel: BoardViewModel = hiltViewModel(),
) {
    val threads = viewModel.catalog.collectAsLazyPagingItems()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.title.ifBlank { "/${viewModel.boardId}/" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                count = threads.itemCount,
                key = threads.itemKey { it.key.thread.value },
            ) { index ->
                val thread = threads[index] ?: return@items
                CatalogCard(
                    thread = thread,
                    onClick = {
                        onOpenThread(
                            viewModel.providerId,
                            viewModel.boardId,
                            thread.key.thread.value,
                            thread.originalPost.subject ?: "/${viewModel.boardId}/",
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CatalogCard(
    thread: CatalogThread,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            thread.originalPost.attachments.firstOrNull()?.let { media ->
                MediaThumbnail(attachment = media)
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                thread.originalPost.subject?.let { subject ->
                    Text(
                        text = subject,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(modifier = Modifier.padding(top = 4.dp)) {
                    PostCommentText(comment = thread.originalPost.comment)
                }
                Text(
                    text = "${thread.stats.replyCount} replies · ${thread.stats.imageCount} images",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
