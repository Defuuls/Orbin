package com.orbin.feature.thread

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.PostId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThumbnailSize
import com.orbin.core.ui.date.formatRelativeTime
import com.orbin.core.ui.post.PostCommentText
import com.orbin.media.image.MediaThumbnail
import com.orbin.uinext.MediaCell
import com.orbin.uinext.MessageScreen
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ThreadLayout
import com.orbin.uinext.ThreadScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import com.orbin.uinext.Post as NextPost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextThreadScreen(
    onOpenMedia: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onOpenCommands: (() -> Unit)? = null,
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val firstUnreadPostId by viewModel.firstUnreadPostId.collectAsStateWithLifecycle()
    val thumbnailSize by viewModel.thumbnailSize.collectAsStateWithLifecycle()
    val mediaScroll by viewModel.mediaScrollEnabled.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()
    val initialScrollPosition by viewModel.initialScrollPosition.collectAsStateWithLifecycle()
    val initialScrollLoaded by viewModel.initialScrollLoaded.collectAsStateWithLifecycle()
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
                    firstUnreadPostId = firstUnreadPostId,
                    initialScrollPosition = initialScrollPosition,
                    initialScrollLoaded = initialScrollLoaded,
                    snackbarHostState = snackbarHostState,
                    thumbnailSize = thumbnailSize,
                    mediaScroll = mediaScroll,
                    viewModel = viewModel,
                    onOpenMedia = onOpenMedia,
                    onOpenCommands = onOpenCommands,
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
    firstUnreadPostId: PostId?,
    initialScrollPosition: ThreadScrollPosition?,
    initialScrollLoaded: Boolean,
    snackbarHostState: SnackbarHostState,
    thumbnailSize: ThumbnailSize,
    mediaScroll: Boolean,
    viewModel: ThreadViewModel,
    onOpenMedia: (Int) -> Unit,
    onOpenCommands: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val thread = state.thread
    val listState = rememberLazyListState()
    var layout by rememberSaveable(thread.key) { mutableStateOf(ThreadLayout.POSTS) }
    val collapsed =
        rememberSaveable(
            thread.key,
            saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
        ) { mutableStateListOf<String>() }
    val presentation = remember(thread) { thread.toPresentationIndex() }
    val rows = presentation.rows

    var scrollTarget by remember(thread.key) { mutableStateOf<String?>(null) }
    var initialScrollRestored by rememberSaveable(thread.key) { mutableStateOf(false) }

    fun saveVisiblePost(flush: Boolean = false) {
        if (layout != ThreadLayout.POSTS || rows.isEmpty()) return
        val visibleItem = listState.firstVisibleItemIndex
        val postIndex = (visibleItem - 1).coerceIn(0, rows.lastIndex)
        val offset = if (visibleItem == 0) 0 else listState.firstVisibleItemScrollOffset
        viewModel.saveScrollPosition(rows[postIndex].post.id, offset)
        if (flush) viewModel.flushScrollPosition()
    }

    fun openMedia(index: Int) {
        saveVisiblePost(flush = true)
        onOpenMedia(index)
    }

    LaunchedEffect(initialScrollLoaded, initialScrollPosition, rows, initialScrollRestored) {
        if (!initialScrollLoaded || initialScrollRestored) return@LaunchedEffect
        initialScrollPosition?.let { saved ->
            val target = rows.indexOfFirst { it.post.id == saved.postId }
            if (target >= 0) {
                listState.scrollToItem(target + 1, saved.offsetPx.coerceAtLeast(0))
            }
        }
        initialScrollRestored = true
    }

    LaunchedEffect(listState, rows, layout, initialScrollRestored) {
        if (layout != ThreadLayout.POSTS || rows.isEmpty() || !initialScrollRestored) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest {
                delay(SCROLL_TRACK_SETTLE_MS)
                saveVisiblePost()
            }
    }

    DisposableEffect(thread.key, layout, rows) {
        onDispose { saveVisiblePost(flush = true) }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        ThreadScreen(
            subject = thread.subject ?: viewModel.title,
            board = "/${thread.key.board.value}/",
            posts = presentation.rowModels,
            subtitle = thread.subtitleText(state.fromSavedCopy),
            watching = isBookmarked,
            layout = layout,
            onLayoutChange = {
                if (layout == ThreadLayout.POSTS) saveVisiblePost(flush = true)
                layout = it
            },
            files = presentation.fileCells,
            fileColumns = thumbnailSize.threadGridColumns(),
            onOpenFile = { cell -> presentation.mediaIndex[cell.id]?.let(::openMedia) },
            fileTile = { cell, tileModifier ->
                presentation.attachmentsById[cell.id]?.let { attachment ->
                    MediaThumbnail(
                        attachment = attachment,
                        modifier = tileModifier.clip(RoundedCornerShape(10.dp)),
                        onClick = { presentation.mediaIndex[cell.id]?.let(::openMedia) },
                    )
                }
            },
            collapsed = collapsed.toSet(),
            onToggleCollapse = { post ->
                if (!collapsed.remove(post.id)) collapsed.add(post.id)
            },
            listState = listState,
            scrollToPostId = scrollTarget,
            firstUnreadPostId = firstUnreadPostId?.value?.toString(),
            onScrollConsumed = { scrollTarget = null },
            onWatch = viewModel::toggleBookmark,
            onDownloadAll = viewModel::downloadAllMedia,
            onShare = viewModel::exportLinks,
            showRail = onOpenCommands != null,
            onSearch = onOpenCommands ?: {},
            body = { row ->
                presentation.rowsById[row.id]?.let { entry ->
                    PostCommentText(
                        comment = entry.post.comment,
                        onQuoteClick = { target -> scrollTarget = target.value.toString() },
                    )
                }
            },
            media = { row, tileModifier ->
                val postAttachments =
                    presentation.rowsById[row.id]
                        ?.post
                        ?.attachments
                        .orEmpty()
                PostMedia(
                    attachments = postAttachments,
                    scrollable = mediaScroll,
                    modifier = tileModifier,
                    onOpen = { id -> presentation.mediaIndex[id]?.let(::openMedia) },
                )
            },
        )
    }
    SnackbarHost(hostState = snackbarHostState)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostMedia(
    attachments: List<MediaAttachment>,
    scrollable: Boolean,
    modifier: Modifier,
    onOpen: (String) -> Unit,
) {
    if (attachments.isEmpty()) return
    val shape = RoundedCornerShape(14.dp)
    if (attachments.size == 1) {
        val only = attachments.first()
        MediaThumbnail(
            attachment = only,
            modifier = modifier.aspectRatio(only.threadAspectRatio()).clip(shape),
            onClick = { onOpen(only.id) },
        )
        return
    }
    if (scrollable) {
        val pagerState = rememberPagerState(pageCount = { attachments.size })
        val stableAspectRatio = attachments.first().threadAspectRatio()
        Column {
            HorizontalPager(state = pagerState) { page ->
                val attachment = attachments[page]
                MediaThumbnail(
                    attachment = attachment,
                    modifier = modifier.aspectRatio(stableAspectRatio).clip(shape),
                    onClick = { onOpen(attachment.id) },
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${pagerState.currentPage + 1} of ${attachments.size}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Column {
        attachments.forEachIndexed { index, attachment ->
            MediaThumbnail(
                attachment = attachment,
                modifier = modifier.aspectRatio(attachment.threadAspectRatio()).clip(shape),
                onClick = { onOpen(attachment.id) },
            )
            if (index < attachments.lastIndex) Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun MediaAttachment.threadAspectRatio(): Float =
    aspectRatio.coerceIn(MIN_THREAD_MEDIA_ASPECT, MAX_THREAD_MEDIA_ASPECT)

internal data class ThreadRow(
    val post: com.orbin.core.model.Post,
    val row: NextPost,
)

private data class ThreadPresentationIndex(
    val rows: List<ThreadRow>,
    val rowModels: List<NextPost>,
    val rowsById: Map<String, ThreadRow>,
    val attachmentsById: Map<String, MediaAttachment>,
    val mediaIndex: Map<String, Int>,
    val fileCells: List<MediaCell>,
)

private fun Thread.toPresentationIndex(): ThreadPresentationIndex {
    val rows = toRows()
    val attachments = allPosts.flatMap { it.attachments }
    val boardLabel = "/${key.board.value}/"
    return ThreadPresentationIndex(
        rows = rows,
        rowModels = rows.map { it.row },
        rowsById = rows.associateBy { it.row.id },
        attachmentsById = attachments.associateBy { it.id },
        mediaIndex = attachments.withIndex().associate { (index, media) -> media.id to index },
        fileCells = attachments.map { MediaCell(id = it.id, board = boardLabel) },
    )
}

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
    return if (fromSavedCopy) "$base  ·  saved copy" else base
}

private fun ThumbnailSize.threadGridColumns(): Int =
    when (this) {
        ThumbnailSize.COMPACT -> COMPACT_COLUMNS
        ThumbnailSize.MEDIUM -> MEDIUM_COLUMNS
        ThumbnailSize.LARGE -> LARGE_COLUMNS
        ThumbnailSize.FILL -> FILL_COLUMNS
    }

private const val COMPACT_COLUMNS = 4
private const val MEDIUM_COLUMNS = 3
private const val LARGE_COLUMNS = 2
private const val FILL_COLUMNS = 1
private const val MIN_THREAD_MEDIA_ASPECT = 0.75f
private const val MAX_THREAD_MEDIA_ASPECT = 2f
private const val SCROLL_TRACK_SETTLE_MS = 180L
