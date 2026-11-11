package com.orbin.feature.thread

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.common.link.SafeExternalLinks
import com.orbin.core.common.result.DataError
import com.orbin.core.model.PostId
import com.orbin.core.model.Thread
import com.orbin.core.model.isWideWindow
import com.orbin.core.reader.ReaderPostComment
import com.orbin.core.reader.mediaPostId
import com.orbin.core.reader.readerThreadStatus
import com.orbin.media.image.MediaThumbnail
import com.orbin.uinext.MessageScreen
import com.orbin.uinext.NextPullToRefresh
import com.orbin.uinext.NextSnackbarHost
import com.orbin.uinext.NextSnackbarHostState
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ReaderAppBar
import com.orbin.uinext.ReaderErrorState
import com.orbin.uinext.ReaderFailureCause
import com.orbin.uinext.ReaderNavigation
import com.orbin.uinext.ReaderRefreshFailure
import com.orbin.uinext.ReaderThreadSkeleton
import com.orbin.uinext.ThreadLayout
import com.orbin.uinext.ThreadScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun NextThreadScreen(
    onOpenMedia: (Int) -> Unit,
    modifier: Modifier = Modifier,
    mediaScrollIndex: Int? = null,
    onMediaScrollConsumed: () -> Unit = {},
    viewModel: ThreadViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
) {
    val configuration = LocalConfiguration.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isWatching by viewModel.isWatching.collectAsStateWithLifecycle()
    val isSaved by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val firstUnreadPostId by viewModel.firstUnreadPostId.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()
    val initialScrollPosition by viewModel.initialScrollPosition.collectAsStateWithLifecycle()
    val initialScrollLoaded by viewModel.initialScrollLoaded.collectAsStateWithLifecycle()
    val snackbarHostState = remember { NextSnackbarHostState() }
    LaunchedEffect(exportMessage) {
        exportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeExportMessage()
        }
    }

    NextTheme {
        when (val state = uiState) {
            is ThreadUiState.Loading ->
                ThreadStateFrame(viewModel.title, onBack, modifier) {
                    ReaderThreadSkeleton(
                        wideWindow = isWideWindow(configuration.screenWidthDp, configuration.screenHeightDp),
                    )
                }
            is ThreadUiState.Blocked ->
                ThreadStateFrame(viewModel.title, onBack, modifier) {
                    MessageScreen(
                        title = stringResource(R.string.next_thread_blocked_title),
                        subtitle = stringResource(R.string.next_thread_blocked),
                    )
                }
            is ThreadUiState.Error ->
                ThreadStateFrame(viewModel.title, onBack, modifier) {
                    ReaderErrorState(cause = state.cause.readerCause(), onRetry = viewModel::refresh)
                }
            is ThreadUiState.Success ->
                LoadedThread(
                    state = state,
                    isRefreshing = isRefreshing,
                    isWatching = isWatching,
                    isSaved = isSaved,
                    firstUnreadPostId = firstUnreadPostId,
                    initialScrollPosition = initialScrollPosition,
                    initialScrollLoaded = initialScrollLoaded,
                    snackbarHostState = snackbarHostState,
                    mediaScrollIndex = mediaScrollIndex,
                    onMediaScrollConsumed = onMediaScrollConsumed,
                    viewModel = viewModel,
                    onOpenMedia = onOpenMedia,
                    modifier = modifier,
                    onBack = onBack,
                )
        }
    }
}

@Composable
private fun LoadedThread(
    state: ThreadUiState.Success,
    isRefreshing: Boolean,
    isWatching: Boolean,
    isSaved: Boolean,
    firstUnreadPostId: PostId?,
    initialScrollPosition: ThreadScrollPosition?,
    initialScrollLoaded: Boolean,
    snackbarHostState: NextSnackbarHostState,
    mediaScrollIndex: Int?,
    onMediaScrollConsumed: () -> Unit,
    viewModel: ThreadViewModel,
    onOpenMedia: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val haptics = LocalHapticFeedback.current
    val thread = state.thread
    val listState = rememberLazyListState()
    var layout by rememberSaveable(thread.key) { mutableStateOf(ThreadLayout.POSTS) }
    val collapsed =
        rememberSaveable(
            thread.key,
            saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
        ) { mutableStateListOf<String>() }
    val presentation = state.presentation
    val statusText = readerThreadStatus(presentation)
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

    LaunchedEffect(mediaScrollIndex, rows, layout, initialScrollRestored) {
        val index = mediaScrollIndex ?: return@LaunchedEffect
        if (!initialScrollRestored || layout != ThreadLayout.POSTS || rows.isEmpty()) return@LaunchedEffect

        val targetPostId = presentation.postIdForMediaIndex(index)
        val target = targetPostId?.let { postId -> rows.indexOfFirst { it.post.id == postId } } ?: -1
        if (target >= 0) {
            collapsed.remove(rows[target].row.id)
            listState.scrollToItem(target + 1)
            viewModel.saveScrollPosition(rows[target].post.id, 0)
            viewModel.flushScrollPosition()
        }
        onMediaScrollConsumed()
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

    NextPullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ThreadScreen(
                subject = thread.subject ?: viewModel.title,
                board = "/${thread.key.board.value}/",
                siteName = thread.key.provider.value,
                onBack = onBack,
                posts = presentation.rowModels,
                subtitle = statusText,
                statusText = statusText,
                reloading = isRefreshing,
                watching = isWatching,
                saved = isSaved,
                onToggleSaved = viewModel::toggleBookmark,
                layout = layout,
                onLayoutChange = {
                    if (layout == ThreadLayout.POSTS) saveVisiblePost(flush = true)
                    layout = it
                },
                files = presentation.fileCells,
                onOpenFile = { cell -> presentation.mediaIndex[cell.id]?.let(::openMedia) },
                fileTile = { cell, tileModifier ->
                    presentation.attachmentsById[cell.id]?.let { attachment ->
                        MediaThumbnail(
                            attachment = attachment.copy(isSpoiler = false),
                            fullResolution = false,
                            modifier = tileModifier,
                            // The screen clips each tile's corners itself.
                            cornerRadius = 0.dp,
                            onClick = { presentation.mediaIndex[cell.id]?.let(::openMedia) },
                        )
                    }
                },
                collapsed = collapsed.toSet(),
                onToggleCollapse = { post ->
                    haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    if (!collapsed.remove(post.id)) collapsed.add(post.id)
                },
                listState = listState,
                scrollToPostId = scrollTarget,
                firstUnreadPostId = firstUnreadPostId?.value?.toString(),
                onScrollConsumed = { scrollTarget = null },
                onWatch = {
                    haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    viewModel.toggleWatch()
                },
                onDownloadAll = {
                    haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    viewModel.downloadAllMedia()
                },
                onSavePage = {
                    haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    viewModel.saveThreadPage()
                },
                onShare = {
                    val shareUrl = viewModel.threadWebUrl
                    val shareSubject = thread.subject ?: viewModel.title
                    val shareText =
                        if (shareSubject.isNotBlank() && shareUrl != null) {
                            "$shareSubject\n$shareUrl"
                        } else {
                            shareUrl ?: shareSubject
                        }
                    val sendIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                    context.startActivity(
                        Intent.createChooser(sendIntent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                body = { row ->
                    presentation.rowsById[row.id]?.let { entry ->
                        ReaderPostComment(
                            row = entry,
                            onQuoteClick = { target ->
                                val id = target.value.toString()
                                collapsed.remove(id)
                                scrollTarget = id
                            },
                            onLinkClick = { link -> SafeExternalLinks.open(context, link) },
                        )
                    }
                },
                media = { row, tileModifier ->
                    // The post's first file, cropped to the square the screen gives it. The viewer
                    // pages through the rest.
                    presentation.rowsById[row.id]?.post?.attachments?.firstOrNull()?.let { first ->
                        MediaThumbnail(
                            attachment = first.copy(isSpoiler = false),
                            fullResolution = false,
                            modifier = tileModifier,
                            cornerRadius = 0.dp,
                            onClick = { presentation.firstMediaIndex(row.id)?.let(::openMedia) },
                        )
                    }
                },
                onReload = viewModel::refresh,
                onOpenBrowser =
                    viewModel.threadWebUrl?.let { url ->
                        {
                            SafeExternalLinks.open(context, url)
                        }
                    },
                onPostReference = { id ->
                    collapsed.remove(id)
                    scrollTarget = id
                },
                wideWindow = isWideWindow(configuration.screenWidthDp, configuration.screenHeightDp),
            )
            state.refreshFailure?.let { failure ->
                ReaderRefreshFailure(
                    cause = failure.readerCause(),
                    onRetry = viewModel::refresh,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            NextSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun ThreadStateFrame(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        ReaderAppBar(
            title = title.ifBlank { stringResource(R.string.thread_title_fallback) },
            navigation = if (onBack != null) ReaderNavigation.BACK else ReaderNavigation.NONE,
            onNavigation = onBack ?: {},
        )
        content()
    }
}

private fun DataError?.readerCause(): ReaderFailureCause =
    when (this) {
        is DataError.Offline, is DataError.Timeout -> ReaderFailureCause.NETWORK
        is DataError.NotFound -> ReaderFailureCause.NOT_FOUND
        is DataError.Server, is DataError.RateLimited -> ReaderFailureCause.SERVER
        else -> ReaderFailureCause.UNKNOWN
    }

internal fun Thread.postIdForMediaIndex(mediaIndex: Int): PostId? = mediaPostId(mediaIndex)

private const val SCROLL_TRACK_SETTLE_MS = 180L
