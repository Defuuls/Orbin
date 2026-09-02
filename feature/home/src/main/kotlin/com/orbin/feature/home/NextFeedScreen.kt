package com.orbin.feature.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.FeedSort
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.activityMillis
import com.orbin.core.model.comparator
import com.orbin.core.model.matchesFilterTokens
import com.orbin.core.model.mutedTagTokens
import com.orbin.core.ui.date.formatRelativeTime
import com.orbin.media.image.MediaThumbnail
import com.orbin.media.video.VideoPlayer
import com.orbin.media.video.canAutoplayInFeed
import com.orbin.uinext.FeedLayout
import com.orbin.uinext.FeedRow
import com.orbin.uinext.FeedScreen
import com.orbin.uinext.MessageScreen
import com.orbin.uinext.NextTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val RELATIVE_TIME_TICK_MS = 60_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextFeedScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    onOpenCommands: () -> Unit,
    onOpenSettings: () -> Unit,
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
    LaunchedEffect(refreshRequest) {
        if (refreshRequest > 0) viewModel.refresh()
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val visited by viewModel.visitedThreadKeys.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val layoutName by viewModel.feedLayoutName.collectAsStateWithLifecycle()
    val layout = FeedLayout.entries.firstOrNull { it.name == layoutName } ?: FeedLayout.LIST
    val nowMillis by
        produceState(initialValue = System.currentTimeMillis()) {
            while (true) {
                delay(RELATIVE_TIME_TICK_MS)
                value = System.currentTimeMillis()
            }
        }

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
                val mutedTokens = remember(settings.mutedTags) { settings.mutedTagTokens() }
                var entries by remember { mutableStateOf<List<FeedEntry>>(emptyList()) }
                var presentationReady by remember { mutableStateOf(false) }
                var activePreviewId by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(state.boards, visited, filter, settings.feedSort, mutedTokens) {
                    entries =
                        withContext(Dispatchers.Default) {
                            feedEntries(
                                feeds = state.boards,
                                visited = visited,
                                nowMillis = System.currentTimeMillis(),
                                filter = filter,
                                sort = settings.feedSort,
                                mutedTokens = mutedTokens,
                            )
                        }
                    presentationReady = true
                }

                if (!presentationReady) {
                    MessageScreen(
                        title = stringResource(R.string.next_feed_title),
                        subtitle = stringResource(R.string.next_feed_loading),
                        where = stringResource(R.string.next_feed_title).takeIf { showRail },
                        action = railAction,
                        onSearch = onOpenCommands,
                        modifier = modifier,
                    )
                } else if (entries.isEmpty()) {
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
                    val rows = remember(entries) { entries.map { it.row } }
                    val baseSubtitle = feedSubtitle(entries.size, state.boards.size)
                    val statusSubtitle =
                        when {
                            state.failedBoards.isNotEmpty() ->
                                "$baseSubtitle · ${pluralStringResource(
                                    R.plurals.next_feed_failed_boards,
                                    state.failedBoards.size,
                                    state.failedBoards.size,
                                )}"

                            state.stale -> stringResource(R.string.next_feed_stale_status, baseSubtitle)
                            else -> {
                                val ageMinutes = ((nowMillis - state.loadedAtMillis) / RELATIVE_TIME_TICK_MS).toInt()
                                if (ageMinutes > 0) {
                                    stringResource(R.string.next_feed_updated_status, baseSubtitle, "${ageMinutes}m")
                                } else {
                                    baseSubtitle
                                }
                            }
                        }
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = viewModel::refresh,
                        modifier = modifier.fillMaxSize(),
                    ) {
                        FeedScreen(
                            rows = rows,
                            subtitle = statusSubtitle,
                            railDetail = boardCountLabel(state.boards.size),
                            showRail = showRail,
                            layout = layout,
                            showSizeControl = true,
                            onLayoutChange = { viewModel.setFeedLayoutName(it.name) },
                            sortLabel = settings.feedSort.label,
                            onSort = viewModel::cycleFeedSort,
                            filter = filter.takeIf { it.isNotBlank() },
                            onClearFilter = onClearFilter,
                            hideRailOnScroll = hideRailOnScroll,
                            onChromeVisibleChange = onChromeVisibleChange,
                            scrollToTopRequest = scrollToTopRequest,
                            railAction = railAction,
                            onSearch = onOpenCommands,
                            onSettings = onOpenSettings,
                            onActivePreviewChanged = { activePreviewId = it },
                            activityText = { row ->
                                byId[row.id]
                                    ?.let { formatRelativeTime(it.activityMillis, nowMillis).orEmpty() }
                                    ?: row.activity
                            },
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
                                        autoplay = settings.autoplayVideosInFeed && row.id == activePreviewId,
                                        fitWholeImage = layout == FeedLayout.LIST,
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

@Composable
private fun FeedPreview(
    attachment: MediaAttachment,
    autoplay: Boolean,
    modifier: Modifier = Modifier,
    fitWholeImage: Boolean = false,
) {
    if (canAutoplayInFeed(attachment, autoplay)) {
        VideoPlayer(url = attachment.sourceUrl, modifier = modifier, autoPlay = true, muted = true)
    } else {
        MediaThumbnail(
            attachment = attachment,
            modifier = modifier,
            contentScale = if (fitWholeImage) ContentScale.Fit else ContentScale.Crop,
        )
    }
}

internal data class FeedEntry(
    val key: ThreadKey,
    val title: String,
    val attachment: MediaAttachment?,
    val activityMillis: Long,
    val row: FeedRow,
)

internal fun feedEntries(
    feeds: List<SubscribedBoardFeed>,
    visited: Set<ThreadKey>,
    nowMillis: Long,
    filter: String = "",
    sort: FeedSort = FeedSort.BOARD,
    mutedTokens: Set<String> = emptySet(),
): List<FeedEntry> =
    feeds
        .flatMap { feed -> feed.threads }
        .filter { thread -> thread.matchesFeedFilter(filter) }
        .sortedWith(sort.comparator())
        .map { thread ->
            thread.toEntry(
                visited = visited,
                nowMillis = nowMillis,
                muted = mutedTokens.isNotEmpty() && thread.matchesFilterTokens(mutedTokens),
            )
        }

internal fun CatalogThread.matchesFeedFilter(filter: String): Boolean {
    val token = filter.trim()
    if (token.isEmpty()) return true
    return key.board.value.contains(token, ignoreCase = true) ||
        originalPost.subject?.contains(token, ignoreCase = true) == true ||
        originalPost.comment.raw.contains(token, ignoreCase = true) ||
        originalPost.poster.name?.contains(token, ignoreCase = true) == true ||
        originalPost.poster.tripcode?.contains(token, ignoreCase = true) == true ||
        originalPost.attachments
            .firstOrNull()
            ?.originalFileName
            ?.contains(token, ignoreCase = true) == true
}

private fun CatalogThread.toEntry(
    visited: Set<ThreadKey>,
    nowMillis: Long,
    muted: Boolean,
): FeedEntry {
    val title = originalPost.subject ?: "No.${key.thread.value}"
    val activityMillis = activityMillis()
    return FeedEntry(
        key = key,
        title = title,
        attachment = originalPost.attachments.firstOrNull(),
        activityMillis = activityMillis,
        row =
            FeedRow(
                id = "${key.board.value}/${key.thread.value}",
                subject = title,
                board = "/${key.board.value}/",
                activity = formatRelativeTime(activityMillis, nowMillis).orEmpty(),
                replies = stats.replyCount,
                media = stats.imageCount,
                hasPreview = originalPost.attachments.isNotEmpty(),
                read = key in visited,
                muted = muted,
            ),
    )
}

@Composable
internal fun feedSubtitle(
    threads: Int,
    boards: Int,
): String =
    stringResource(
        R.string.next_feed_subtitle,
        pluralStringResource(R.plurals.next_feed_thread_count, threads, threads),
        boardCountLabel(boards),
    )

@Composable
internal fun boardCountLabel(boards: Int): String =
    pluralStringResource(R.plurals.next_feed_board_count, boards, boards)
