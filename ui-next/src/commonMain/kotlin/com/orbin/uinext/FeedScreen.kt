package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_feed_title
import com.orbin.uinext.resources.next_search_title
import com.orbin.uinext.resources.tiktok_feed_menu
import com.orbin.uinext.tokens.ReaderAmoledColors
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource

/** Stable tags for the immersive reader's navigation and page actions. */
object TikTokFeedTags {
    const val PAGER = "tiktok_feed_pager"
    const val HEADER = "tiktok_feed_header"
    const val CAPTION = "tiktok_feed_caption"
    const val ACTIONS = "tiktok_feed_actions"
    const val FOLLOWING = "tiktok_feed_following"
    const val FOR_YOU = "tiktok_feed_for_you"

    fun page(id: String): String = "tiktok_feed_page:$id"
}

/**
 * Home is one thread per vertical page. The media slot owns playback gestures; opening a thread
 * belongs to its caption and reply action, so pausing a clip never accidentally navigates away.
 *
 * The density arguments remain source-compatible with existing integrations. Home always uses
 * the full viewport; catalog density is still controlled by [BoardCatalog].
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun FeedScreen(
    rows: List<FeedRow>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showRail: Boolean = true,
    onOpenRow: (FeedRow) -> Unit = {},
    onSettings: (() -> Unit)? = null,
    thumbnail: (@Composable (FeedRow, Modifier) -> Unit)? = null,
    activityText: @Composable (FeedRow) -> String = { it.activity },
    onActivePreviewChanged: (String?) -> Unit = {},
    hideRailOnScroll: Boolean = true,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    onCompactTitleVisibleChange: (Boolean) -> Unit = {},
    onOpenBoards: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    headerContent: @Composable () -> Unit = {},
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    groupByBoard: Boolean = false,
    columns: Int? = null,
    uniformCards: Boolean = false,
    onOpenSearch: (() -> Unit)? = null,
    onOpenSaved: (() -> Unit)? = null,
    onOpenWatched: (() -> Unit)? = null,
    onToggleWatch: ((FeedRow) -> Unit)? = null,
    onToggleSaved: ((FeedRow) -> Unit)? = null,
    onShareRow: ((FeedRow) -> Unit)? = null,
    sourceLabels: List<String> = emptyList(),
    selectedSource: Int = 0,
    onSelectSource: (Int) -> Unit = {},
    statusMessage: String? = null,
    mediaContent: (@Composable (FeedRow, Modifier, Boolean) -> Unit)? = null,
    onOpenBoard: ((FeedRow) -> Unit)? = null,
    onUpcomingPreviewChanged: (String?) -> Unit = {},
    following: Boolean? = null,
    onFollowingChange: (Boolean) -> Unit = {},
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    onLoadMore: (() -> Unit)? = null,
    onOpenReplies: ((FeedRow) -> Unit)? = null,
    repliesOpenComments: Boolean = onOpenReplies == null,
    loadMoreKey: Any? = null,
) {
    var localFollowing by rememberSaveable { mutableStateOf(false) }
    val showingFollowing = following ?: localFollowing
    // The adapters own board membership; watching a thread must never change its feed tab.
    val visibleRows = rows
    var discoveryPage by rememberSaveable(selectedSource) { mutableIntStateOf(0) }
    var followingPage by rememberSaveable(selectedSource) { mutableIntStateOf(0) }
    val pager =
        key(showingFollowing, selectedSource) {
            rememberPagerState(
                initialPage = if (showingFollowing) followingPage else discoveryPage,
                pageCount = { visibleRows.size },
            )
        }
    LaunchedEffect(pager, showingFollowing) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            if (showingFollowing) followingPage = page else discoveryPage = page
        }
    }
    var relocation by remember(pager) { mutableStateOf<FeedRelocation?>(null) }
    val settledId by remember(pager) {
        derivedStateOf {
            relocation?.id ?: (
                pager.layoutInfo.visiblePagesInfo
                    .firstOrNull { it.index == pager.settledPage }
                    ?.key as? String
            )
        }
    }
    val visibleIds = remember(visibleRows) { visibleRows.map { it.id } }
    // Capture the previous laid-out key before the pager applies this data-set change.
    // Reordering retains the same thread; removal explicitly chooses a valid remaining page.
    // A normal swipe or an appended row does not request a scroll and keeps its player alive.
    val anchorId = settledId
    LaunchedEffect(pager, visibleIds) {
        if (visibleIds.isNotEmpty()) {
            val retainedIndex = visibleIds.indexOf(anchorId)
            val target = retainedIndex.takeIf { it >= 0 } ?: pager.currentPage.coerceIn(visibleIds.indices)
            if (retainedIndex < 0 || target != pager.currentPage || target != pager.settledPage) {
                // PagerState briefly retains the old settled index while it relocates. Keep
                // the retained identity rather than activating that index's new neighbour.
                val request = retainedIndex.takeIf { it >= 0 }?.let { FeedRelocation(visibleIds[it]) }
                relocation = request
                try {
                    pager.scrollToPage(target)
                } finally {
                    if (relocation === request) relocation = null
                }
            }
        }
    }
    val previewCallback by rememberUpdatedState(onActivePreviewChanged)
    val upcomingCallback by rememberUpdatedState(onUpcomingPreviewChanged)
    val chromeCallback by rememberUpdatedState(onChromeVisibleChange)
    val titleCallback by rememberUpdatedState(onCompactTitleVisibleChange)
    val loadMoreCallback by rememberUpdatedState(onLoadMore)
    LaunchedEffect(pager, visibleRows.size, onLoadMore != null, loadMoreKey) {
        snapshotFlow { pager.settledPage >= visibleRows.lastIndex - 2 && visibleRows.isNotEmpty() }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) loadMoreCallback?.invoke() }
    }

    LaunchedEffect(Unit) {
        chromeCallback(true)
        titleCallback(false)
    }
    // Keep the settled clip alive during a drag, including a cancelled swipe. Only settling
    // onto another source-qualified row transfers ownership to a new player.
    LaunchedEffect(pager, visibleRows) {
        snapshotFlow {
            val settledIndex = visibleRows.indexOfFirst { it.id == settledId }
            val active = visibleRows.getOrNull(settledIndex)?.takeIf { it.hasPreview && !it.muted }?.id
            val upcoming =
                visibleRows
                    .getOrNull((settledIndex + 1).takeIf { settledIndex >= 0 } ?: -1)
                    ?.takeIf { it.hasPreview && !it.muted }
                    ?.id
            active to upcoming
        }.distinctUntilChanged().collect { (active, upcoming) ->
            previewCallback(active)
            upcomingCallback(upcoming)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            previewCallback(null)
            upcomingCallback(null)
        }
    }

    val hasTabs =
        onOpenBoards != null ||
            onOpenDownloads != null ||
            onOpenSearch != null ||
            onOpenSaved != null ||
            onOpenWatched != null
    val onDestination: ((NextDestination) -> Unit)? =
        if (hasTabs) {
            { destination ->
                when (destination) {
                    NextDestination.FEED -> Unit
                    NextDestination.BOARDS -> onOpenBoards?.invoke()
                    NextDestination.DOWNLOADS -> onOpenDownloads?.invoke()
                    NextDestination.SEARCH -> onOpenSearch?.invoke()
                    NextDestination.SAVED -> onOpenSaved?.invoke()
                    NextDestination.WATCHED -> onOpenWatched?.invoke()
                    NextDestination.SETTINGS -> onSettings?.invoke()
                }
            }
        } else {
            null
        }

    var feedMenuVisible by rememberSaveable { mutableStateOf(false) }
    NextScaffold(
        where = stringResource(Res.string.next_feed_title),
        modifier = modifier.fillMaxSize(),
        railVisible = true,
        destination = NextDestination.FEED.takeIf { showRail && hasTabs },
        onDestination = onDestination.takeIf { showRail },
        drawerDestinations =
            buildSet {
                add(NextDestination.FEED)
                if (onOpenBoards != null) add(NextDestination.BOARDS)
                if (onOpenWatched != null) add(NextDestination.WATCHED)
                if (onOpenSaved != null) add(NextDestination.SAVED)
                if (onOpenSearch != null) add(NextDestination.SEARCH)
                if (onSettings != null) add(NextDestination.SETTINGS)
            },
        immersive = true,
        actions =
            listOfNotNull(
                ReaderAction(ReaderIcons.MoreVertical, stringResource(Res.string.tiktok_feed_menu)) {
                    feedMenuVisible =
                        true
                },
                onOpenSearch?.let {
                    ReaderAction(ReaderIcons.Search, stringResource(Res.string.next_search_title), it)
                },
            ),
    ) { bottomPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(ReaderAmoledColors.background)
                .padding(bottom = bottomPadding.calculateBottomPadding()),
        ) {
            if (visibleRows.isNotEmpty()) {
                VerticalPager(
                    state = pager,
                    key = { page -> visibleRows.getOrNull(page)?.id ?: "feed-pending:$page" },
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize().testTag(TikTokFeedTags.PAGER),
                ) { page ->
                    val row = visibleRows.getOrNull(page) ?: return@VerticalPager
                    TikTokFeedPage(
                        row = row,
                        active = row.id == settledId && !row.muted,
                        thumbnail = thumbnail,
                        mediaContent = mediaContent,
                        activity = activityText(row),
                        headerStatus = statusMessage,
                        onOpen = { onOpenRow(row) },
                        onOpenReplies = { (onOpenReplies ?: onOpenRow)(row) },
                        repliesOpenComments = repliesOpenComments,
                        onOpenBoard = onOpenBoard?.let { action -> { action(row) } } ?: onOpenBoards,
                        onToggleWatch = onToggleWatch,
                        onToggleSaved = onToggleSaved,
                        onShare = onShareRow,
                    )
                }
            } else {
                TikTokFeedEmpty(
                    following = showingFollowing,
                    onOpenBoards = onOpenBoards,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onRetry = onRetry,
                )
            }
            TikTokFeedHeader(
                menuVisible = feedMenuVisible,
                onMenuVisibleChange = { feedMenuVisible = it },
                following = showingFollowing,
                onFollowingChange = {
                    localFollowing = it
                    onFollowingChange(it)
                },
                subtitle = subtitle,
                statusMessage = statusMessage,
                onSettings = onSettings,
                onSearch = onOpenSearch,
                onOpenBoards = onOpenBoards,
                onOpenDownloads = onOpenDownloads,
                headerContent = headerContent,
                query = query,
                onQueryChange = onQueryChange,
                sourceLabels = sourceLabels,
                selectedSource = selectedSource,
                onSelectSource = onSelectSource,
            )
        }
    }
}

private class FeedRelocation(
    val id: String,
)

/** Catalog density remains independent of Home's single-page presentation. */
internal val FEED_CARD_MIN_WIDTH = 360.dp
internal const val FEED_EXCERPT_LINES = 3
internal const val FEED_SIZE_MIN_DP = MEDIA_SIZE_MIN_DP
internal const val FEED_SIZE_MAX_DP = MEDIA_SIZE_MAX_DP
internal const val FEED_SIZE_STEPS = MEDIA_SIZE_STEPS
