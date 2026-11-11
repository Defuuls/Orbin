package com.orbin.feature.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.isWideWindow
import com.orbin.core.ui.date.formatRelativeTime
import com.orbin.media.image.MediaThumbnail
import com.orbin.uinext.BoardScreen
import com.orbin.uinext.CatalogMode
import com.orbin.uinext.FeedRow
import com.orbin.uinext.MessageScreen
import com.orbin.uinext.NextPullToRefresh
import com.orbin.uinext.NextTheme
import com.orbin.uinext.sourceLabel
import com.orbin.core.model.CatalogMode as StoredCatalogMode

/**
 * The redesigned board catalog, wired to the same [BoardViewModel] the current one uses.
 *
 * Rows are handed over by index because the catalog is paged. [selectedThread] is the thread open
 * beside the catalog in two panes, so its row can show as selected.
 */
@Composable
fun NextBoardScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenMedia: ((board: String) -> Unit)? = null,
    selectedThread: Long? = null,
    viewModel: BoardViewModel = hiltViewModel(),
) {
    val threads = viewModel.catalog.collectAsLazyPagingItems()
    val catalogItemKey =
        threads.itemKey { thread -> "${thread.key.board.value}/${thread.key.thread.value}" }
    val visitedThreadIds by viewModel.visitedThreadIds.collectAsStateWithLifecycle()
    val watchedUnread by viewModel.watchedUnread.collectAsStateWithLifecycle()
    val catalogSort by viewModel.catalogSort.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current

    val board = "/${viewModel.boardId}/"
    val snapshot = threads.itemSnapshotList
    val byThreadId =
        remember(snapshot) {
            snapshot.items.associateBy { it.key.thread.value }
        }
    // Memoize FeedRows the way the feed/media walls do — BoardCatalog hits rowAt for
    // key, contentType, and content (up to 3× per cell) on every composition otherwise.
    val rows =
        remember(snapshot, visitedThreadIds, watchedUnread, board, threads.itemCount) {
            List(threads.itemCount) { index ->
                snapshot.getOrNull(index)?.toRow(board, visitedThreadIds, watchedUnread)
            }
        }
    val rowFor: (Int) -> FeedRow? = { index ->
        if (index in 0 until threads.itemCount) {
            threads[index]
        }
        rows.getOrNull(index)
    }

    NextTheme {
        val refreshState = threads.loadState.refresh
        if (refreshState is LoadState.Loading && threads.itemCount == 0) {
            MessageScreen(
                title = board,
                subtitle = stringResource(R.string.board_loading),
                where = board,
                modifier = modifier,
                skeleton = true,
            )
            return@NextTheme
        }
        if (refreshState is LoadState.Error && threads.itemCount == 0) {
            MessageScreen(
                title = board,
                subtitle = stringResource(refreshState.error.catalogFailure().messageRes),
                actionLabel = stringResource(R.string.board_try_again),
                onAction = threads::retry,
                where = board,
                modifier = modifier,
            )
            return@NextTheme
        }
        if (threads.itemCount == 0) {
            MessageScreen(
                title = board,
                subtitle = stringResource(R.string.next_board_empty),
                actionLabel = stringResource(R.string.board_refresh),
                onAction = threads::refresh,
                where = board,
                modifier = modifier,
            )
            return@NextTheme
        }
        NextPullToRefresh(
            isRefreshing = refreshState is LoadState.Loading,
            onRefresh = threads::refresh,
            modifier = modifier,
        ) {
            BoardScreen(
                board = board,
                description = viewModel.title,
                itemCount = threads.itemCount,
                rowAt = rowFor,
                rowKey = catalogItemKey,
                sortLabel = catalogSort.label,
                onSort = viewModel::cycleCatalogSort,
                onOpenMedia = onOpenMedia?.let { open -> { open(viewModel.boardId) } },
                onOpenRow = { row ->
                    row.threadId()?.let { id ->
                        onOpenThread(
                            viewModel.providerId,
                            viewModel.boardId,
                            id,
                            byThreadId[id]?.originalPost?.subject ?: "No.$id",
                        )
                    }
                },
                mode =
                    when (settings.catalogMode) {
                        StoredCatalogMode.LIST -> CatalogMode.LIST
                        StoredCatalogMode.GRID -> CatalogMode.GRID
                    },
                onModeChange = { mode ->
                    viewModel.setCatalogMode(
                        when (mode) {
                            CatalogMode.LIST -> StoredCatalogMode.LIST
                            CatalogMode.GRID -> StoredCatalogMode.GRID
                        },
                    )
                },
                onReload = threads::refresh,
                reloading = refreshState is LoadState.Loading,
                wideWindow = isWideWindow(configuration.screenWidthDp, configuration.screenHeightDp),
                selectedRowId = selectedThread?.let { "${viewModel.boardId}/$it" },
                thumbnail = { row, tileModifier ->
                    row.threadId()?.let { id ->
                        byThreadId[id]?.originalPost?.attachments?.firstOrNull()?.let { attachment ->
                            MediaThumbnail(
                                attachment = attachment,
                                fullResolution = false,
                                contentScale = ContentScale.Crop,
                                // The row and the card clip their own thumbnail corners.
                                cornerRadius = 0.dp,
                                modifier = tileModifier,
                            )
                        }
                    }
                },
            )
        }
    }
}

/** The thread number encoded in a row's id, or null if the id is not one this screen made. */
private fun FeedRow.threadId(): Long? = id.substringAfterLast('/').toLongOrNull()

private fun CatalogThread.toRow(
    board: String,
    visited: Set<Long>,
    unreadByThread: Map<Long, Int> = emptyMap(),
): FeedRow =
    FeedRow(
        id = "${key.board.value}/${key.thread.value}",
        subject = originalPost.subject ?: "No.${key.thread.value}",
        board = board,
        site = sourceLabel(key.provider.value),
        poster = originalPost.poster.name?.takeIf { it.isNotBlank() } ?: "Anonymous",
        mediaLabel =
            originalPost.attachments
                .firstOrNull()
                ?.extension
                ?.uppercase()
                .orEmpty(),
        excerpt = originalPost.comment.plainText(),
        activity =
            formatRelativeTime(
                if (stats.lastModifiedMillis > 0L) stats.lastModifiedMillis else originalPost.createdAtMillis,
            ).orEmpty(),
        replies = stats.replyCount,
        media = stats.imageCount,
        hasPreview = originalPost.attachments.isNotEmpty(),
        read = key.thread.value in visited,
        unread = unreadByThread[key.thread.value] ?: 0,
        mediaAspectRatio = originalPost.attachments.firstOrNull()?.previewAspectRatio ?: 0f,
    )
