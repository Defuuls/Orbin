package com.orbin.feature.board

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.orbin.core.model.CatalogThread
import com.orbin.core.ui.date.formatRelativeTime
import com.orbin.media.image.MediaThumbnail
import com.orbin.uinext.BoardScreen
import com.orbin.uinext.FeedLayout
import com.orbin.uinext.FeedRow
import com.orbin.uinext.MessageScreen
import com.orbin.uinext.NextTheme

/**
 * The redesigned board catalog, wired to the same [BoardViewModel] the current one uses.
 *
 * A catalog is a feed scoped to one board, so it reuses the feed's grid and image layouts.
 * Rows are handed over by index because the catalog is paged.
 */
@Composable
fun NextBoardScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    onOpenCommands: () -> Unit,
    modifier: Modifier = Modifier,
    hideRailOnScroll: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    viewModel: BoardViewModel = hiltViewModel(),
) {
    val threads = viewModel.catalog.collectAsLazyPagingItems()
    val visitedThreadIds by viewModel.visitedThreadIds.collectAsStateWithLifecycle()
    val watchedUnread by viewModel.watchedUnread.collectAsStateWithLifecycle()
    val catalogSort by viewModel.catalogSort.collectAsStateWithLifecycle()
    var layout by rememberSaveable { mutableStateOf(FeedLayout.GRID) }

    val board = "/${viewModel.boardId}/"
    val byThreadId =
        remember(threads.itemSnapshotList) {
            threads.itemSnapshotList.items.associateBy { it.key.thread.value }
        }
    val rowFor: (Int) -> FeedRow? = { index ->
        threads[index]?.toRow(board, visitedThreadIds, watchedUnread)
    }

    NextTheme {
        if (threads.itemCount == 0) {
            MessageScreen(
                title = board,
                subtitle = stringResource(R.string.next_board_empty),
                modifier = modifier,
            )
            return@NextTheme
        }
        BoardScreen(
            board = board,
            description = viewModel.title,
            itemCount = threads.itemCount,
            rowAt = rowFor,
            layout = layout,
            onLayoutChange = { layout = if (it == FeedLayout.IMAGES) it else FeedLayout.GRID },
            sortLabel = catalogSort.label,
            onSort = viewModel::cycleCatalogSort,
            onSearch = onOpenCommands,
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
            hideRailOnScroll = hideRailOnScroll,
            onChromeVisibleChange = onChromeVisibleChange,
            thumbnail = { row, tileModifier ->
                row.threadId()?.let { id ->
                    byThreadId[id]?.originalPost?.attachments?.firstOrNull()?.let { attachment ->
                        MediaThumbnail(
                            attachment = attachment,
                            contentScale = ContentScale.Crop,
                            modifier = tileModifier.clip(RoundedCornerShape(14.dp)),
                        )
                    }
                }
            },
            modifier = modifier,
        )
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
        activity =
            formatRelativeTime(
                if (stats.lastModifiedMillis > 0L) stats.lastModifiedMillis else originalPost.createdAtMillis,
            ).orEmpty(),
        replies = stats.replyCount,
        media = stats.imageCount,
        hasPreview = originalPost.attachments.isNotEmpty(),
        read = key.thread.value in visited,
        unread = unreadByThread[key.thread.value] ?: 0,
    )
