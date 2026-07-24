package com.orbin.feature.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.Bookmark
import com.orbin.core.model.ThreadKey
import com.orbin.core.ui.state.EmptyView

/** Bookmarks list with watch toggle, unread badges, and remove, hosted inside the gallery. */
@Composable
fun GalleryBookmarksTab(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    viewModel: GalleryBookmarksViewModel = hiltViewModel(),
) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()

    if (bookmarks.isEmpty()) {
        EmptyView("No bookmarks yet", Modifier.fillMaxSize())
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
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
            Row {
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

private fun ThreadKey.threadString(): String = "${provider.value}/${board.value}/${thread.value}"
