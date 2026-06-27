package com.orbin.feature.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.Bookmark
import com.orbin.core.ui.state.EmptyView

/** Bookmarks list with watch toggle, unread badges, and remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bookmarks") }) },
    ) { padding ->
        if (bookmarks.isEmpty()) {
            EmptyView("No bookmarks yet", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(bookmarks, key = { it.key.threadString() }) { bookmark ->
                BookmarkRow(
                    bookmark = bookmark,
                    onOpen = {
                        onOpenThread(
                            bookmark.key.provider.value,
                            bookmark.key.board.value,
                            bookmark.key.thread.value,
                            bookmark.title,
                        )
                    },
                    onToggleWatch = { viewModel.toggleWatched(bookmark.key, !bookmark.isWatched) },
                    onRemove = { viewModel.remove(bookmark.key) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    onOpen: () -> Unit,
    onToggleWatch: () -> Unit,
    onRemove: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = { Text(bookmark.title) },
        supportingContent = {
            Text("/${bookmark.key.board.value}/ · ${bookmark.latestReplyCount} replies")
        },
        leadingContent =
            if (bookmark.hasUnread) {
                { Badge { Text(bookmark.unreadCount.toString()) } }
            } else {
                null
            },
        trailingContent = {
            androidx.compose.foundation.layout.Row {
                IconButton(onClick = onToggleWatch) {
                    Icon(
                        imageVector =
                            if (bookmark.isWatched) {
                                Icons.Filled.Notifications
                            } else {
                                Icons.Outlined.Notifications
                            },
                        contentDescription = if (bookmark.isWatched) "Unwatch" else "Watch",
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove")
                }
            }
        },
    )
}

private fun com.orbin.core.model.ThreadKey.threadString(): String = "${provider.value}/${board.value}/${thread.value}"
