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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.designsystem.component.ModernConfirmDialog
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
    var pendingRemove by remember { mutableStateOf<Bookmark?>(null) }

    if (bookmarks.isEmpty()) {
        EmptyView(stringResource(R.string.gallery_no_bookmarks), Modifier.fillMaxSize())
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
                onRemove = { pendingRemove = bookmark },
            )
            HorizontalDivider()
        }
    }

    pendingRemove?.let { bookmark ->
        ModernConfirmDialog(
            title = stringResource(R.string.gallery_remove_bookmark_title),
            text = stringResource(R.string.gallery_remove_bookmark_text, bookmark.title),
            confirmLabel = stringResource(R.string.gallery_remove),
            onConfirm = {
                viewModel.remove(bookmark.key)
                pendingRemove = null
            },
            onDismiss = { pendingRemove = null },
        )
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
            Text(stringResource(R.string.gallery_bookmark_summary, bookmark.key.board.value, bookmark.latestReplyCount))
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
                        contentDescription =
                            if (bookmark.isWatched) {
                                stringResource(R.string.gallery_unwatch)
                            } else {
                                stringResource(R.string.gallery_watch)
                            },
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.gallery_remove))
                }
            }
        },
    )
}

private fun ThreadKey.threadString(): String = "${provider.value}/${board.value}/${thread.value}"
