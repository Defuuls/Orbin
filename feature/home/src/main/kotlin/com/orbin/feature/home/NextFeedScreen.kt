package com.orbin.feature.home

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.FeedSort
import com.orbin.core.model.HomeFeedMode
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.activityMillis
import com.orbin.core.model.comparator
import com.orbin.core.model.isWideWindow
import com.orbin.core.ui.date.formatRelativeTime
import com.orbin.media.image.MediaThumbnail
import com.orbin.uinext.CatalogMode
import com.orbin.uinext.FeedRow
import com.orbin.uinext.FeedScreen
import com.orbin.uinext.NextPullToRefresh
import com.orbin.uinext.NextTheme
import com.orbin.uinext.sourceLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val RELATIVE_TIME_TICK_MS = 60_000L

@Composable
fun NextFeedScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    showRail: Boolean = true,
    hideRailOnScroll: Boolean = true,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    onCompactTitleVisibleChange: (Boolean) -> Unit = {},
    onOpenBoards: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    headerContent: @Composable () -> Unit = {},
    viewModel: SubscribedFeedViewModel = hiltViewModel(),
    providerFilter: String? = null,
    onOpenSearch: (() -> Unit)? = null,
    onOpenSaved: (() -> Unit)? = null,
    onOpenWatched: (() -> Unit)? = null,
    sourceLabels: List<String> = emptyList(),
    selectedSource: Int = 0,
    onSelectSource: (Int) -> Unit = {},
    onOpenBoard: ((provider: String, board: String, title: String) -> Unit)? = null,
    onOpenThreadVideos: (provider: String, board: String, thread: Long, title: String) -> Unit = onOpenThread,
    onOpenMedia: (
        (
            provider: String,
            board: String,
            thread: Long,
            attachment: MediaAttachment,
        ) -> Unit
    )? = null,
    downloadBadge: Int? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mode by viewModel.feedMode.collectAsStateWithLifecycle()
    LaunchedEffect(providerFilter) { viewModel.setProviderFilter(providerFilter) }
    val actionsViewModel: FeedActionsViewModel = hiltViewModel()
    val activeDownloads =
        if (downloadBadge != null) {
            downloadBadge
        } else {
            val badgeModel: FeedDownloadBadgeViewModel = hiltViewModel()
            val count by badgeModel.activeCount.collectAsStateWithLifecycle()
            count
        }
    val bookmarks by actionsViewModel.bookmarks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val visited by viewModel.visitedThreadKeys.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val wideWindow =
        with(density) {
            isWideWindow(
                windowSize.width
                    .toDp()
                    .value
                    .toInt(),
                windowSize.height
                    .toDp()
                    .value
                    .toInt(),
            )
        }
    // Home now draws bounded catalog thumbnails. Only the dedicated media viewer owns a
    // player; a retained feed model must not keep the former inline preview prefetch alive.
    DisposableEffect(viewModel) {
        viewModel.clearFeedImagePrefetch()
        viewModel.clearFeedVideoPrefetch()
        onDispose {
            viewModel.clearFeedImagePrefetch()
            viewModel.clearFeedVideoPrefetch()
        }
    }
    var localQuery by rememberSaveable { mutableStateOf("") }
    val presentationScope =
        FeedPresentationScope(mode, providerFilter, localQuery, settings.hideNsfwBoards, settings.coverViolentMedia)
    val state =
        (uiState as? SubscribedFeedUiState.Success)?.takeIf {
            it.mode == mode &&
                it.providerFilter == providerFilter &&
                it.hideNsfwBoards == settings.hideNsfwBoards &&
                it.coverViolentMedia == settings.coverViolentMedia
        }
    var mapped by remember { mutableStateOf<MappedFeed?>(null) }
    LaunchedEffect(state?.boards, visited, presentationScope) {
        val current = state ?: return@LaunchedEffect
        val entries =
            withContext(Dispatchers.Default) {
                feedEntries(
                    current.boards,
                    visited,
                    System.currentTimeMillis(),
                    localQuery,
                    FeedSort.ACTIVITY,
                    videosOnly = true,
                )
            }
        mapped = MappedFeed(presentationScope, current.boards, entries)
    }
    // Retain rows through incremental mapping, while immediately excluding another tab/source
    // and threads removed by a newly applied visibility policy.
    val eligibleKeys =
        remember(state?.boards) {
            state
                ?.boards
                .orEmpty()
                .flatMap { it.threads }
                .mapTo(mutableSetOf()) { it.key }
        }
    val entries =
        mapped
            ?.takeIf { it.scope == presentationScope }
            ?.entries
            .orEmpty()
            .filter { it.key in eligibleKeys }
    val mappingCurrent = state != null && mapped?.scope == presentationScope && mapped?.boards === state.boards
    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(RELATIVE_TIME_TICK_MS)
            value = System.currentTimeMillis()
        }
    }
    val byId = remember(entries) { entries.associateBy { it.row.id } }
    val bookmarksByKey = remember(bookmarks) { bookmarks.associateBy { it.key } }
    val rows =
        remember(entries, bookmarksByKey) {
            entries.map { entry ->
                val bookmark = bookmarksByKey[entry.key]
                entry.row.copy(bookmarked = bookmark != null, watched = bookmark?.isWatched == true)
            }
        }
    val failedBoardCount = state?.boards?.count { it.failed } ?: 0
    val baseSubtitle = feedSubtitle(entries.size, state?.boards?.size ?: 0)
    val statusSubtitle =
        when {
            failedBoardCount > 0 -> "$baseSubtitle · ${pluralStringResource(
                R.plurals.next_feed_failed_boards,
                failedBoardCount,
                failedBoardCount,
            )}"
            state?.stale == true -> stringResource(R.string.next_feed_stale_status, baseSubtitle)
            else -> null
        }
    val loading =
        when {
            uiState is SubscribedFeedUiState.Error -> false
            state == null -> true
            entries.isEmpty() -> !mappingCurrent || state.isLoadingMore
            else -> false
        }
    val emptyError = state?.errorMessage?.takeIf { mappingCurrent && entries.isEmpty() && !state.isLoadingMore }
    NextTheme {
        NextPullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = modifier.fillMaxSize(),
        ) {
            val catalogMode = CatalogMode.CARD
            FeedScreen(
                rows = rows,
                isLoading = loading,
                errorMessage = (uiState as? SubscribedFeedUiState.Error)?.message ?: emptyError,
                onRetry = viewModel::refresh,
                onLoadMore =
                    (viewModel::loadMoreDiscovery).takeIf {
                        state?.canLoadMore == true &&
                            !state.isLoadingMore &&
                            mappingCurrent &&
                            (state.errorMessage == null || entries.isNotEmpty())
                    },
                loadMoreKey = state?.discoveryLimit,
                subtitle = baseSubtitle,
                statusMessage = statusSubtitle,
                showRail = showRail,
                headerContent = headerContent,
                sourceLabels = sourceLabels,
                selectedSource = selectedSource,
                onSelectSource = onSelectSource,
                query = localQuery,
                onQueryChange = { localQuery = it },
                mode = catalogMode,
                downloadBadge = activeDownloads,
                onModeChange = null,
                wideWindow = wideWindow,
                hideRailOnScroll = hideRailOnScroll,
                onChromeVisibleChange = onChromeVisibleChange,
                onCompactTitleVisibleChange = onCompactTitleVisibleChange,
                onSettings = onOpenSettings,
                onOpenBoards = onOpenBoards,
                onOpenDownloads = onOpenDownloads,
                onOpenSearch = onOpenSearch,
                onOpenSaved = onOpenSaved,
                onOpenWatched = onOpenWatched,
                onToggleWatch = { row -> byId[row.id]?.let(actionsViewModel::toggleWatch) },
                onToggleSaved = { row -> byId[row.id]?.let(actionsViewModel::toggleSaved) },
                onShareRow = { row ->
                    byId[row.id]?.let { entry ->
                        val intent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, entry.title)
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    listOfNotNull(entry.title, actionsViewModel.shareUrl(entry.key)).joinToString("\n"),
                                )
                            }
                        context.startActivity(
                            Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                activityText = { row ->
                    byId[row.id]?.let { formatRelativeTime(it.activityMillis, nowMillis).orEmpty() }
                        ?: row.activity
                },
                onOpenRow = { row ->
                    byId[row.id]?.let {
                        onOpenThread(
                            it.key.provider.value,
                            it.key.board.value,
                            it.key.thread.value,
                            it.title,
                        )
                    }
                },
                onOpenReplies = { row ->
                    byId[row.id]?.let {
                        val open = if (settings.openThreadComments) onOpenThread else onOpenThreadVideos
                        open(it.key.provider.value, it.key.board.value, it.key.thread.value, it.title)
                    }
                },
                onOpenMedia = { row ->
                    byId[row.id]?.let { entry ->
                        val attachment = entry.attachment
                        if (onOpenMedia != null && attachment != null) {
                            onOpenMedia(
                                entry.key.provider.value,
                                entry.key.board.value,
                                entry.key.thread.value,
                                attachment,
                            )
                        } else {
                            onOpenThreadVideos(
                                entry.key.provider.value,
                                entry.key.board.value,
                                entry.key.thread.value,
                                entry.title,
                            )
                        }
                    }
                },
                repliesOpenComments = settings.openThreadComments,
                onOpenBoard =
                    onOpenBoard?.let { open ->
                        { row -> byId[row.id]?.let { open(it.key.provider.value, it.key.board.value, row.boardTitle) } }
                    },
                thumbnail = { row, tileModifier ->
                    byId[row.id]?.attachment?.let { attachment ->
                        MediaThumbnail(
                            // The shared reader owns spoiler reveal before composing the image.
                            attachment.copy(isSpoiler = false),
                            fullResolution = catalogMode.sharpensThumbnails,
                            contentScale = ContentScale.Crop,
                            cornerRadius = 0.dp,
                            modifier = tileModifier,
                        )
                    }
                },
            )
        }
    }
}

private data class FeedPresentationScope(
    val mode: HomeFeedMode,
    val source: String?,
    val query: String,
    val hideNsfw: Boolean,
    val coverViolent: Boolean,
)

private data class MappedFeed(
    val scope: FeedPresentationScope,
    val boards: List<SubscribedBoardFeed>,
    val entries: List<FeedEntry>,
)

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
    videosOnly: Boolean = false,
): List<FeedEntry> =
    feeds
        .flatMap { feed -> feed.threads }
        .filter { thread -> thread.matchesFeedFilter(filter) }
        .sortedWith(sort.comparator())
        .mapNotNull { thread ->
            val preview =
                thread.originalPost.attachments.firstOrNull {
                    !videosOnly || it.type == MediaType.VIDEO && it.sourceUrl.isNotBlank()
                }
            if (videosOnly &&
                preview == null
            ) {
                null
            } else {
                thread.toEntry(visited = visited, nowMillis = nowMillis, preview = preview)
            }
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
    preview: MediaAttachment?,
): FeedEntry {
    val title = originalPost.subject ?: "No.${key.thread.value}"
    val activityMillis = activityMillis()
    return FeedEntry(
        key = key,
        title = title,
        attachment = preview,
        activityMillis = activityMillis,
        row =
            FeedRow(
                id = "${key.provider.value}/${key.board.value}/${key.thread.value}",
                subject = title,
                board = "/${key.board.value}/",
                site = sourceLabel(key.provider.value),
                poster = originalPost.poster.name?.takeIf { it.isNotBlank() } ?: "Anonymous",
                mediaLabel =
                    preview
                        ?.extension
                        ?.uppercase()
                        .orEmpty(),
                activity = formatRelativeTime(activityMillis, nowMillis).orEmpty(),
                replies = stats.replyCount,
                media = stats.imageCount,
                hasPreview = preview != null,
                imageSpoiler = preview?.isSpoiler == true,
                read = key in visited,
                // The parsed comment, not the raw markup: entities decoded, line breaks kept.
                excerpt = originalPost.comment.plainText(),
                threadNumber = key.thread.value.toString(),
                mediaAspectRatio = preview?.previewAspectRatio ?: 0f,
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
