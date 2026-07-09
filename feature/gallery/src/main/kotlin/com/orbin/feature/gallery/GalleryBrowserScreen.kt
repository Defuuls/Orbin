package com.orbin.feature.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.Board
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.ui.state.EmptyView
import com.orbin.media.image.OrbinAsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryBrowserScreen(
    onOpenMedia: (provider: String, board: String, thread: Long, startIndex: Int) -> Unit,
    viewModel: GalleryBrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gallery")
                        Text(
                            text = "Browse media by board and thread",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GalleryControls(
                state = state,
                onSelectBoard = viewModel::selectBoard,
                onSelectThread = viewModel::selectThread,
                onPreload = viewModel::preloadSelectedThread,
            )

            when {
                state.loadingBoards || state.loadingThreads ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                state.media.isEmpty() ->
                    EmptyView(state.message ?: "No media in this thread", Modifier.fillMaxSize())

                else ->
                    MediaGrid(
                        media = state.media,
                        onOpenMedia = { index ->
                            val thread = state.selectedThread ?: return@MediaGrid
                            onOpenMedia(
                                state.provider.value,
                                thread.key.board.value,
                                thread.key.thread.value,
                                index,
                            )
                        },
                    )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryControls(
    state: GalleryBrowserUiState,
    onSelectBoard: (Board) -> Unit,
    onSelectThread: (CatalogThread) -> Unit,
    onPreload: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoardDropdown(
            boards = state.boards,
            selected = state.selectedBoard,
            enabled = !state.loadingBoards,
            onSelect = onSelectBoard,
        )
        ThreadDropdown(
            threads = state.threads,
            selected = state.selectedThread,
            enabled = !state.loadingThreads && state.threads.isNotEmpty(),
            onSelect = onSelectThread,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${state.media.size} media items",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                enabled = state.selectedThread != null && !state.preloadingThread,
                onClick = onPreload,
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null)
                Text(
                    text = if (state.preloadingThread) "Preloading" else "Preload thread",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (state.preloadingThread) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { state.progressValue },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = state.progressMessage ?: "Preparing media in the background…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.message != null) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardDropdown(
    boards: List<Board>,
    selected: Board?,
    enabled: Boolean,
    onSelect: (Board) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            value = selected?.let { "/${it.id.value}/ - ${it.title}" }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Board") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            boards.forEach { board ->
                DropdownMenuItem(
                    text = { Text("/${board.id.value}/ - ${board.title}") },
                    onClick = {
                        expanded = false
                        onSelect(board)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadDropdown(
    threads: List<CatalogThread>,
    selected: CatalogThread?,
    enabled: Boolean,
    onSelect: (CatalogThread) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            value = selected?.label().orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Thread") },
            trailingIcon = {
                Icon(Icons.Filled.ExpandMore, contentDescription = null)
            },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            threads.forEach { thread ->
                DropdownMenuItem(
                    text = { Text(thread.label(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        expanded = false
                        onSelect(thread)
                    },
                )
            }
        }
    }
}

@Composable
private fun MediaGrid(
    media: List<MediaAttachment>,
    onOpenMedia: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(
            items = media,
            // Index-qualified: a thread can legitimately repost the same attachment.
            key = { index, attachment -> "${attachment.id}#$index" },
            contentType = { _, _ -> "media-tile" },
        ) { index, attachment ->
            MediaTile(attachment = attachment, onClick = { onOpenMedia(index) })
        }
    }
}

@Composable
private fun MediaTile(
    attachment: MediaAttachment,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        OrbinAsyncImage(
            url = attachment.thumbnailUrl,
            contentDescription = attachment.originalFileName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (attachment.type == MediaType.VIDEO || attachment.type == MediaType.AUDIO) {
            Surface(color = Color.Black.copy(alpha = 0.64f), shape = RoundedCornerShape(999.dp)) {
                Text(
                    text = if (attachment.type == MediaType.AUDIO) "AUDIO" else "VIDEO",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private fun CatalogThread.label(): String = originalPost.subject?.takeIf { it.isNotBlank() } ?: "No.${key.thread.value}"
