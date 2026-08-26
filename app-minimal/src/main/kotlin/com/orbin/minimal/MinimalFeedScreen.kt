package com.orbin.minimal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.ThreadKey
import com.orbin.core.ui.scrollbar.FastScrollbar
import com.orbin.core.ui.state.EmptyView
import com.orbin.core.ui.state.ErrorView
import com.orbin.core.ui.state.LoadingView
import com.orbin.feature.home.SubscribedFeedUiState
import com.orbin.feature.home.SubscribedFeedViewModel
import com.orbin.media.image.MediaThumbnail
import com.orbin.media.video.VideoPlayer

/**
 * The entire app, near enough: every thread across every subscribed board in one list, newest
 * first, with a board tag on each row.
 *
 * It reuses the full client's [SubscribedFeedViewModel] rather than reimplementing the fetch —
 * same providers, same caching, same filters, same permanent content filter. Only the presentation
 * is pared back: no board headers, no layout modes, no in-feed search. A row is a preview, a board
 * tag, a title and a reply count.
 *
 * A row whose opening post carries a video plays it, muted, while the row is on screen. Unlike the
 * full client this is not a setting — this build has no settings screen — so it is always on, and
 * a feed spanning every subscribed board will use mobile data without being asked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinimalFeedScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    onOpenBoards: () -> Unit,
    viewModel: SubscribedFeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val providerId by viewModel.providerId.collectAsStateWithLifecycle()
    val visitedKeys by viewModel.visitedThreadKeys.collectAsStateWithLifecycle()

    MinimalFeedContent(
        uiState = uiState,
        isRefreshing = isRefreshing,
        providerId = providerId,
        visitedKeys = visitedKeys,
        onRefresh = viewModel::refresh,
        onOpenThread = onOpenThread,
        onOpenBoards = onOpenBoards,
    )
}

/**
 * The feed's rendering, with no view model attached, so it can be composed against fixed state —
 * which is what the screenshot tests do to cover the loading, error, empty and populated cases.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MinimalFeedContent(
    uiState: SubscribedFeedUiState,
    isRefreshing: Boolean,
    providerId: String,
    visitedKeys: Set<ThreadKey>,
    onRefresh: () -> Unit,
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    onOpenBoards: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Which rows are on screen. Autoplay follows this: a video starts as its row scrolls in and is
    // disposed as it scrolls out, so only what a reader can actually see is ever playing.
    val visibleKeys by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.mapTo(mutableSetOf()) { it.key } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.minimal_feed_title)) },
                actions = {
                    IconButton(onClick = onOpenBoards) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.minimal_edit_boards),
                        )
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val state = uiState) {
                SubscribedFeedUiState.Loading -> LoadingView()
                is SubscribedFeedUiState.Error -> ErrorView(state.message, onRetry = onRefresh)
                is SubscribedFeedUiState.Success -> {
                    val threads = remember(state.boards) { state.boards.flattenToFeed() }
                    when {
                        state.boards.isEmpty() -> EmptyView(stringResource(R.string.minimal_no_subscriptions))
                        threads.isEmpty() -> EmptyView(stringResource(R.string.minimal_no_threads))
                        else ->
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Resolved once here rather than per row, and reused for the
                                // reader's title so both say the same thing.
                                val untitled = stringResource(R.string.minimal_no_subject)
                                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                    items(threads, key = { it.id }) { row ->
                                        val title = row.title.ifEmpty { untitled }
                                        ThreadRow(
                                            row = row,
                                            title = title,
                                            isRead = row.thread.key in visitedKeys,
                                            autoplay = row.id in visibleKeys,
                                            onClick = {
                                                onOpenThread(
                                                    providerId,
                                                    row.board.id.value,
                                                    row.thread.key.thread.value,
                                                    title,
                                                )
                                            },
                                        )
                                        HorizontalDivider()
                                    }
                                }
                                // A feed spanning every subscribed board is long enough that
                                // flinging to reach the middle of it does not work.
                                FastScrollbar(
                                    listState = listState,
                                    modifier = Modifier.align(Alignment.CenterEnd),
                                )
                            }
                    }
                }
            }
        }
    }
}

/**
 * Preview, board tag, title, reply count. Read threads dim, exactly as they do in the full client.
 *
 * [autoplay] is only ever true while the row is on screen. Scrolling it away flips back to the
 * static thumbnail, which disposes the player rather than merely pausing it — the same arrangement
 * the full client's feed uses, and the reason a long feed does not accumulate players.
 */
@Composable
private fun ThreadRow(
    row: MinimalThread,
    title: String,
    isRead: Boolean,
    autoplay: Boolean,
    onClick: () -> Unit,
) {
    val titleColor =
        if (isRead) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        row.preview?.let { attachment ->
            MinimalPreview(
                attachment = attachment,
                autoplay = autoplay,
                onClick = onClick,
                modifier = Modifier.size(THUMBNAIL_SIZE),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isRead) FontWeight.Normal else FontWeight.Medium,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    pluralStringResource(
                        R.plurals.minimal_thread_summary,
                        row.thread.stats.replyCount,
                        row.board.id.value,
                        row.thread.stats.replyCount,
                    ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The row's preview: a muted, playing video while the row is on screen, otherwise a static
 * thumbnail.
 *
 * A spoilered attachment never autoplays. [MediaThumbnail] blacks it out until tapped, and starting
 * playback would hand over the very thing the spoiler is hiding.
 */
@Composable
private fun MinimalPreview(
    attachment: MediaAttachment,
    autoplay: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        if (autoplay && attachment.type == MediaType.VIDEO && !attachment.isSpoiler) {
            VideoPlayer(
                url = attachment.sourceUrl,
                modifier = Modifier.fillMaxSize(),
                autoPlay = true,
                muted = true,
            )
        } else {
            MediaThumbnail(
                attachment = attachment,
                modifier = Modifier.fillMaxSize(),
                onClick = onClick,
            )
        }
    }
}

private val THUMBNAIL_SIZE = 88.dp
