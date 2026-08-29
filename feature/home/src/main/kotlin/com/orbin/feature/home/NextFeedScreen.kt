package com.orbin.feature.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.orbin.media.video.VideoPlayer
import com.orbin.media.video.canAutoplayInFeed
import com.orbin.uinext.FeedLayout
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
 * The feed it replaced was a list of boards each containing threads, so a quiet board's stale
 * thread sat above a busy board's live one purely because of alphabetical board order. This merges
 * every subscribed board into one list ordered by when each thread last moved, which is the order
 * someone opening a feed is actually looking for.
 *
 * The rail is on: it is the screen's only chrome, and the shell no longer draws a bottom navigation
 * bar here. Its Search opens the command surface, which is how every other destination is reached
 * now that there are no tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextFeedScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    onOpenCommands: () -> Unit,
    modifier: Modifier = Modifier,
    showRail: Boolean = true,
    hideRailOnScroll: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    scrollToTopRequest: Int = 0,
    refreshRequest: Int = 0,
    filter: String = "",
    onClearFilter: () -> Unit = {},
    railAction: String = "Search",
    viewModel: SubscribedFeedViewModel = hiltViewModel(),
) {
    // "Refresh feed" from the command surface, which is where the tablet dock's refresh button and
    // the old top bar's overflow item both ended up.
    LaunchedEffect(refreshRequest) {
        if (refreshRequest > 0) viewModel.refresh()
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val visited by viewModel.visitedThreadKeys.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // Survives rotation but not the process: a layout is how you are reading right now, not a
    // preference, which is why the previous feed did not persist it either.
    var layout by rememberSaveable { mutableStateOf(FeedLayout.LIST) }

    NextTheme {
        when (val state = uiState) {
            is SubscribedFeedUiState.Loading ->
                MessageScreen(
                    title = stringResource(R.string.next_feed_title),
                    subtitle = stringResource(R.string.next_feed_loading),
                    where = stringResource(R.string.next_feed_title).takeIf { showRail },
                    action = railAction,
                    onSearch = onOpenCommands,
                    modifier = modifier,
                )

            is SubscribedFeedUiState.Error ->
                MessageScreen(
                    title = stringResource(R.string.next_feed_unavailable),
                    subtitle = state.message,
                    actionLabel = stringResource(R.string.next_feed_try_again),
                    onAction = viewModel::refresh,
                    where = stringResource(R.string.next_feed_title).takeIf { showRail },
                    action = railAction,
                    onSearch = onOpenCommands,
                    modifier = modifier,
                )

            is SubscribedFeedUiState.Success -> {
                // Recomputed only when the feed or the read-set actually changes; the relative
                // times are resolved here rather than per row so every row on one pass reads
                // against the same clock.
                val entries =
                    remember(state.boards, visited, filter) {
                        feedEntries(state.boards, visited, System.currentTimeMillis(), filter)
                    }
                if (entries.isEmpty()) {
                    // A filter that matches nothing is not an empty feed, and offering "subscribe
                    // to a board" to someone who just mistyped a search would be nonsense.
                    val filtered = filter.isNotBlank()
                    MessageScreen(
                        title = stringResource(R.string.next_feed_title),
                        subtitle =
                            if (filtered) {
                                stringResource(R.string.next_feed_no_matches, filter)
                            } else {
                                stringResource(R.string.next_feed_empty)
                            },
                        actionLabel = if (filtered) stringResource(R.string.next_feed_clear_filter) else null,
                        onAction = onClearFilter,
                        where = stringResource(R.string.next_feed_title).takeIf { showRail },
                        action = railAction,
                        onSearch = onOpenCommands,
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
                            layout = layout,
                            onLayoutChange = { layout = it },
                            filter = filter.takeIf { it.isNotBlank() },
                            onClearFilter = onClearFilter,
                            hideRailOnScroll = hideRailOnScroll,
                            onChromeVisibleChange = onChromeVisibleChange,
                            scrollToTopRequest = scrollToTopRequest,
                            railAction = railAction,
                            onSearch = onOpenCommands,
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
                                    FeedPreview(
                                        attachment = attachment,
                                        autoplay = settings.autoplayVideosInFeed,
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
 * A thread's preview: a playing video where the setting allows it, the static thumbnail otherwise.
 *
 * Feed autoplay is always muted regardless of "Mute by default" — it is ambient, and a feed that
 * starts talking while you scroll is not what that setting is asking for. The same rule the
 * previous feed applied, and the reason [canAutoplayInFeed] exists.
 */
@Composable
private fun FeedPreview(
    attachment: MediaAttachment,
    autoplay: Boolean,
    modifier: Modifier = Modifier,
) {
    if (canAutoplayInFeed(attachment, autoplay)) {
        VideoPlayer(url = attachment.sourceUrl, modifier = modifier, autoPlay = true, muted = true)
    } else {
        MediaThumbnail(attachment = attachment, modifier = modifier)
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
    filter: String = "",
): List<FeedEntry> =
    feeds
        .flatMap { feed -> feed.threads }
        .filter { thread -> thread.matchesFeedFilter(filter) }
        .sortedByDescending { thread -> thread.activityMillis() }
        .map { thread -> thread.toEntry(visited, nowMillis) }

/**
 * Whether a thread survives the feed filter.
 *
 * The haystack is the previous feed's, unchanged: board, subject, the comment's raw markup, the
 * poster's name and tripcode, and the first attachment's original filename. Searching the raw
 * markup rather than the rendered text is what lets a filter find a thread by a link or a quote it
 * contains, and narrowing it here would quietly lose matches people already rely on.
 */
internal fun CatalogThread.matchesFeedFilter(filter: String): Boolean {
    val token = filter.trim().lowercase()
    if (token.isEmpty()) return true
    val haystack =
        listOfNotNull(
            key.board.value,
            originalPost.subject,
            originalPost.comment.raw,
            originalPost.poster.name,
            originalPost.poster.tripcode,
            originalPost.attachments.firstOrNull()?.originalFileName,
        ).joinToString(" ").lowercase()
    return haystack.contains(token)
}

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
