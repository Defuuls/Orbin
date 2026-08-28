package com.orbin.feature.thread

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.PostId
import com.orbin.core.model.Thread
import com.orbin.core.ui.date.formatRelativeTime
import com.orbin.core.ui.post.PostCommentText
import com.orbin.media.image.MediaThumbnail
import com.orbin.uinext.MessageScreen
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ThreadScreen
import com.orbin.uinext.Post as NextPost

/**
 * The redesigned thread reader, wired to the same [ThreadViewModel] the current one uses.
 *
 * The screen itself lives in `:ui-next` and knows nothing about posts, quote links or attachments.
 * Post bodies and media are supplied through slots, so the two things that carry real behaviour —
 * [PostCommentText], which renders greentext, quote links and inline spoilers, and [MediaThumbnail],
 * which covers spoilered attachments — are the shipped components rather than reimplementations.
 *
 * What is new is the indent: a reply steps in once per link in the chain it hangs off, derived from
 * quote links by [replyDepths]. The current reader shows a flat list of cards and leaves the reader
 * to follow quote links by hand to see what answers what. Order is untouched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextThreadScreen(
    onOpenMedia: (Int) -> Unit,
    onOpenCommands: () -> Unit,
    onOpenClassicReader: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()
    val initialScrollPosition by viewModel.initialScrollPosition.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(exportMessage) {
        exportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeExportMessage()
        }
    }

    NextTheme {
        when (val state = uiState) {
            is ThreadUiState.Loading ->
                MessageScreen(
                    title = viewModel.title.ifBlank { stringResource(R.string.thread_title_fallback) },
                    subtitle = stringResource(R.string.next_thread_loading),
                    modifier = modifier,
                )

            is ThreadUiState.Blocked ->
                MessageScreen(
                    title = stringResource(R.string.next_thread_blocked_title),
                    subtitle = stringResource(R.string.next_thread_blocked),
                    modifier = modifier,
                )

            is ThreadUiState.Error ->
                MessageScreen(
                    title = stringResource(R.string.next_thread_unavailable),
                    subtitle = state.message,
                    actionLabel = stringResource(R.string.next_thread_try_again),
                    onAction = viewModel::refresh,
                    modifier = modifier,
                )

            is ThreadUiState.Success ->
                LoadedThread(
                    state = state,
                    isRefreshing = isRefreshing,
                    isBookmarked = isBookmarked,
                    initialScrollPostId = initialScrollPosition?.postId,
                    snackbarHostState = snackbarHostState,
                    viewModel = viewModel,
                    onOpenMedia = onOpenMedia,
                    onOpenCommands = onOpenCommands,
                    onOpenClassicReader = onOpenClassicReader,
                    modifier = modifier,
                )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadedThread(
    state: ThreadUiState.Success,
    isRefreshing: Boolean,
    isBookmarked: Boolean,
    initialScrollPostId: PostId?,
    snackbarHostState: SnackbarHostState,
    viewModel: ThreadViewModel,
    onOpenMedia: (Int) -> Unit,
    onOpenCommands: () -> Unit,
    onOpenClassicReader: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thread = state.thread
    val listState = rememberLazyListState()
    val rows = remember(thread) { thread.toRows() }
    val byId = remember(rows) { rows.associateBy { it.row.id } }
    // Where each attachment sits in the thread's flat media list, which is what the viewer indexes.
    val mediaIndex =
        remember(thread) {
            thread.allPosts
                .flatMap { it.attachments }
                .withIndex()
                .associate { (index, m) -> m.id to index }
        }

    // A quote link names a post; the reader jumps to it rather than leaving the reader to scroll.
    // Also how the saved reading position is restored, which is the same operation.
    var scrollTarget by remember(thread.key) { mutableStateOf(initialScrollPostId?.value?.toString()) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        ThreadScreen(
            subject = thread.subject ?: viewModel.title,
            board = "/${thread.key.board.value}/",
            posts = rows.map { it.row },
            subtitle = thread.subtitleText(state.fromSavedCopy),
            watching = isBookmarked,
            listState = listState,
            scrollToPostId = scrollTarget,
            onScrollConsumed = { scrollTarget = null },
            onWatch = viewModel::toggleBookmark,
            onFiles = { if (mediaIndex.isNotEmpty()) onOpenMedia(0) },
            onDownloadAll = viewModel::downloadAllMedia,
            onShare = viewModel::exportLinks,
            onClassicReader = onOpenClassicReader,
            onSearch = onOpenCommands,
            body = { row ->
                byId[row.id]?.let { entry ->
                    PostCommentText(
                        comment = entry.post.comment,
                        onQuoteClick = { target -> scrollTarget = target.value.toString() },
                    )
                }
            },
            media = { row, tileModifier ->
                byId[row.id]?.post?.attachments?.firstOrNull()?.let { attachment ->
                    MediaThumbnail(
                        attachment = attachment,
                        modifier = tileModifier.clip(RoundedCornerShape(14.dp)),
                        onClick = { mediaIndex[attachment.id]?.let(onOpenMedia) },
                    )
                }
            },
        )
    }
    SnackbarHost(hostState = snackbarHostState)
}

/** A row, plus the post it was made from — the join the screen itself does not carry. */
internal data class ThreadRow(
    val post: com.orbin.core.model.Post,
    val row: NextPost,
)

/**
 * Turns a thread into rows.
 *
 * Spoilers are deliberately not flagged here: on a real post a spoiler is an inline span or a
 * covered attachment, and both are handled by the components in the slots. The screen's own
 * blacked-out block is what it draws when nothing supplies those.
 */
internal fun Thread.toRows(): List<ThreadRow> {
    val posts = allPosts
    val depths = replyDepths(posts)
    val counts = replyCounts(posts)
    return posts.map { post ->
        ThreadRow(
            post = post,
            row =
                NextPost(
                    id = post.id.value.toString(),
                    number = "No.${post.id.value}",
                    time = formatRelativeTime(post.createdAtMillis).orEmpty(),
                    body = "",
                    depth = depths[post.id] ?: 0,
                    hasMedia = post.attachments.isNotEmpty(),
                    replies = counts[post.id] ?: 0,
                ),
        )
    }
}

private fun Thread.subtitleText(fromSavedCopy: Boolean): String {
    val files = allPosts.sumOf { it.attachments.size }
    val base = "${allPosts.size} posts  ·  $files files"
    // A saved copy stops at the moment it was saved, and the reader has to be told: otherwise a
    // thread that has moved on since looks simply quiet.
    return if (fromSavedCopy) "$base  ·  saved copy" else base
}
