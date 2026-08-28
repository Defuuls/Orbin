package com.orbin.feature.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.ThreadKey
import com.orbin.core.ui.date.formatRelativeTime
import com.orbin.media.image.MediaThumbnail
import com.orbin.uinext.FeedRow
import com.orbin.uinext.FeedScreen
import com.orbin.uinext.MessageScreen
import com.orbin.uinext.NextTheme

/**
 * The proposed feed, wired to the real subscribed-feed state.
 *
 * The screen itself lives in `:ui-next` and knows nothing about threads, providers or repositories;
 * everything it renders arrives as already-formatted [FeedRow]s. This file is the whole of the join
 * between the two: it collects the same [SubscribedFeedViewModel] the current feed uses, turns
 * catalog threads into rows, and hands clicks back as navigation.
 *
 * One behavioural difference from [SubscribedFeedScreen], and it is the point of the design: the
 * current feed is a list of boards each containing threads, so a quiet board's stale thread sits
 * above a busy board's live one purely because of alphabetical board order. This merges every
 * subscribed board into one list ordered by when each thread last moved, which is the order someone
 * opening a feed is actually looking for.
 *
 * [showRail] is off while this runs inside the existing app shell, which still supplies the bottom
 * navigation bar. Two bars stacked would be worse than either alone, and the rail only replaces that
 * bar once the command surface it points at exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextFeedScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    showRail: Boolean = false,
    viewModel: SubscribedFeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val visited by viewModel.visitedThreadKeys.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    NextTheme {
        when (val state = uiState) {
            is SubscribedFeedUiState.Loading ->
                MessageScreen(
                    title = stringResource(R.string.next_feed_title),
                    subtitle = stringResource(R.string.next_feed_loading),
                    modifier = modifier,
                )

            is SubscribedFeedUiState.Error ->
                MessageScreen(
                    title = stringResource(R.string.next_feed_unavailable),
                    subtitle = state.message,
                    actionLabel = stringResource(R.string.next_feed_try_again),
                    onAction = viewModel::refresh,
                    modifier = modifier,
                )

            is SubscribedFeedUiState.Success -> {
                // Recomputed only when the feed or the read-set actually changes; the relative
                // times are resolved here rather than per row so every row on one pass reads
                // against the same clock.
                val entries =
                    remember(state.boards, visited) {
                        feedEntries(state.boards, visited, System.currentTimeMillis())
                    }
                if (entries.isEmpty()) {
                    MessageScreen(
                        title = stringResource(R.string.next_feed_title),
                        subtitle = stringResource(R.string.next_feed_empty),
                        modifier = modifier,
                    )
                } else {
                    val byId = remember(entries) { entries.associateBy { it.row.id } }
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = viewModel::refresh,
                        modifier = modifier.fillMaxSize(),
                    ) {
                        FeedScreen(
                            rows = entries.map { it.row },
                            subtitle = feedSubtitle(entries.size, state.boards.size),
                            railDetail = boardCountLabel(state.boards.size),
                            showRail = showRail,
                            onSearch = onOpenSearch,
                            onOpenRow = { row ->
                                byId[row.id]?.let { entry ->
                                    onOpenThread(
                                        entry.key.provider.value,
                                        entry.key.board.value,
                                        entry.key.thread.value,
                                        entry.title,
                                    )
                                }
                            },
                            thumbnail = { row, tileModifier ->
                                byId[row.id]?.attachment?.let { attachment ->
                                    MediaThumbnail(
                                        attachment = attachment,
                                        modifier = tileModifier.clip(RoundedCornerShape(14.dp)),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A row, plus what the row deliberately does not carry: which thread it is and what to draw in its
 * tile. [FeedRow.id] is the join between the two.
 */
internal data class FeedEntry(
    val key: ThreadKey,
    val title: String,
    val attachment: MediaAttachment?,
    val row: FeedRow,
)

/**
 * Flattens the per-board feeds into one list, most recently active first.
 *
 * Threads carry `lastModifiedMillis` from the catalog; where an engine does not supply it the
 * opening post's own timestamp stands in, so a thread with no bump information sorts by age rather
 * than falling to the bottom as if it were from 1970.
 */
internal fun feedEntries(
    feeds: List<SubscribedBoardFeed>,
    visited: Set<ThreadKey>,
    nowMillis: Long,
): List<FeedEntry> =
    feeds
        .flatMap { feed -> feed.threads }
        .sortedByDescending { thread -> thread.activityMillis() }
        .map { thread -> thread.toEntry(visited, nowMillis) }

private fun CatalogThread.activityMillis(): Long =
    if (stats.lastModifiedMillis > 0L) stats.lastModifiedMillis else originalPost.createdAtMillis

private fun CatalogThread.toEntry(
    visited: Set<ThreadKey>,
    nowMillis: Long,
): FeedEntry {
    // The same fallback the current feed uses, so a subjectless thread reads the same in both.
    val title = originalPost.subject ?: "No.${key.thread.value}"
    return FeedEntry(
        key = key,
        title = title,
        attachment = originalPost.attachments.firstOrNull(),
        row =
            FeedRow(
                id = "${key.board.value}/${key.thread.value}",
                subject = title,
                board = "/${key.board.value}/",
                activity = formatRelativeTime(activityMillis(), nowMillis).orEmpty(),
                replies = stats.replyCount,
                media = stats.imageCount,
                hasPreview = originalPost.attachments.isNotEmpty(),
                read = key in visited,
            ),
    )
}

internal fun feedSubtitle(
    threads: Int,
    boards: Int,
): String = "$threads ${plural(threads, "thread")} across $boards ${plural(boards, "board")}"

internal fun boardCountLabel(boards: Int): String = "$boards ${plural(boards, "board")}"

private fun plural(
    count: Int,
    noun: String,
): String = if (count == 1) noun else "${noun}s"
