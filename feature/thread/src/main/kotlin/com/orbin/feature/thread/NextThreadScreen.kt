package com.orbin.feature.thread

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.orbin.core.common.result.DataError
import com.orbin.core.model.PostFilterKind
import com.orbin.core.model.PostId
import com.orbin.core.model.Thread
import com.orbin.core.model.isWideWindow
import com.orbin.core.model.shouldLoadSharpPreview
import com.orbin.core.reader.ReaderPostComment
import com.orbin.core.reader.boardLabel
import com.orbin.core.reader.mediaPostId
import com.orbin.core.reader.readerRows
import com.orbin.core.reader.readerThreadStatus
import com.orbin.core.reader.unseenPostIds
import com.orbin.media.image.MediaThumbnail
import com.orbin.uinext.components.MessageScreen
import com.orbin.uinext.components.NextConfirmDialog
import com.orbin.uinext.components.NextPullToRefresh
import com.orbin.uinext.components.NextSnackbarHost
import com.orbin.uinext.components.NextSnackbarHostState
import com.orbin.uinext.components.NextTheme
import com.orbin.uinext.components.ReaderAppBar
import com.orbin.uinext.components.ReaderNavigation
import com.orbin.uinext.components.rememberThreadInlineMediaState
import com.orbin.uinext.screens.ReaderErrorState
import com.orbin.uinext.screens.ReaderFailureCause
import com.orbin.uinext.screens.ReaderRefreshFailure
import com.orbin.uinext.screens.ReaderThreadSkeleton
import com.orbin.uinext.screens.ThreadLayout
import com.orbin.uinext.screens.ThreadScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

@Composable
fun NextThreadScreen(
    onOpenMedia: (Int) -> Unit,
    modifier: Modifier = Modifier,
    mediaScrollIndex: Int? = null,
    onMediaScrollConsumed: () -> Unit = {},
    viewModel: ThreadViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
    onOpenMediaFiltered: ((Int, com.orbin.uinext.components.MediaKindFilter) -> Unit)? = null,
    initialPostId: Long? = null,
    onInitialPostConsumed: () -> Unit = {},
) {
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isWatching by viewModel.isWatching.collectAsStateWithLifecycle()
    val isSaved by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val firstUnreadPostId by viewModel.firstUnreadPostId.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()
    val initialScrollPosition by viewModel.initialScrollPosition.collectAsStateWithLifecycle()
    val initialScrollLoaded by viewModel.initialScrollLoaded.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.pollWhileVisible() }
    }
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
                        wideWindow = wideWindow,
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
                    onOpenMediaFiltered = onOpenMediaFiltered,
                    initialPostId = initialPostId,
                    onInitialPostConsumed = onInitialPostConsumed,
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
    onOpenMediaFiltered: ((Int, com.orbin.uinext.components.MediaKindFilter) -> Unit)? = null,
    initialPostId: Long? = null,
    onInitialPostConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
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
    val haptics = LocalHapticFeedback.current
    val thread = state.thread
    var layout by rememberSaveable(thread.key) { mutableStateOf(ThreadLayout.POSTS) }
    var mediaFilter by rememberSaveable(thread.key) { mutableStateOf(com.orbin.uinext.components.MediaKindFilter.ALL) }
    val presentation = state.presentation
    val memory by viewModel.readerMemory.collectAsStateWithLifecycle()
    val preferences by viewModel.readerPreferences.collectAsStateWithLifecycle()
    val isMetered by viewModel.isMetered.collectAsStateWithLifecycle()
    val isDataSaver by viewModel.isDataSaver.collectAsStateWithLifecycle()
    val revealed by viewModel.revealedPosts.collectAsStateWithLifecycle()
    val statusText = readerThreadStatus(presentation)
    val rows by produceState<List<com.orbin.uinext.screens.Post>>(
        emptyList(),
        initialScrollLoaded,
        presentation,
        memory.foldedPaths,
        memory.hiddenPostIds,
        memory.hiddenPosterIds,
        memory.seenPostIds,
        preferences.threadLayout,
        preferences.postFilterRules,
        revealed,
    ) {
        if (!initialScrollLoaded) return@produceState
        value =
            withContext(Dispatchers.Default) {
                presentation.readerRows(
                    nested = preferences.threadLayout == com.orbin.core.model.ThreadLayout.NESTED,
                    foldedPaths = memory.foldedPaths,
                    hiddenPostIds = memory.hiddenPostIds,
                    hiddenPosterIds = memory.hiddenPosterIds,
                    postFilters = preferences.postFilterRules,
                    revealedPostIds = revealed,
                    unseenPostIds = presentation.unseenPostIds(memory.seenPostIds),
                )
            }
    }
    val detailRows by produceState<List<com.orbin.uinext.screens.Post>>(
        emptyList(),
        initialScrollLoaded,
        presentation,
        memory.hiddenPostIds,
        memory.hiddenPosterIds,
        memory.seenPostIds,
        preferences.postFilterRules,
        revealed,
    ) {
        if (!initialScrollLoaded) return@produceState
        value =
            withContext(Dispatchers.Default) {
                presentation.readerRows(
                    nested = false,
                    hiddenPostIds = memory.hiddenPostIds,
                    hiddenPosterIds = memory.hiddenPosterIds,
                    postFilters = preferences.postFilterRules,
                    revealedPostIds = revealed,
                    unseenPostIds = presentation.unseenPostIds(memory.seenPostIds),
                )
            }
    }
    val visibleOwners =
        remember(rows, memory.foldedPaths) {
            rows
                .filter {
                    it.hiddenReason == null && !it.preview && it.branchPath !in memory.foldedPaths
                }.mapTo(mutableSetOf()) { it.id }
        }
    val visibleFiles =
        remember(presentation, visibleOwners) {
            presentation.fileCells.filterIndexed { index, _ ->
                presentation.postIdForMediaIndex(index)?.value?.toString() in
                    visibleOwners
            }
        }
    val listState =
        remember(thread.key) {
            val saved = initialScrollPosition.takeIf { initialScrollLoaded }
            val savedIdx = saved?.let { pos -> rows.indexOfFirst { it.id == pos.postId.value.toString() } } ?: -1
            val unreadIdx = firstUnreadPostId?.let { id -> rows.indexOfFirst { it.id == id.value.toString() } } ?: -1
            when {
                savedIdx >= 0 -> LazyListState(savedIdx + 1, saved?.offsetPx?.coerceAtLeast(0) ?: 0)
                unreadIdx > 0 -> LazyListState(unreadIdx + 1, 0)
                else -> LazyListState()
            }
        }

    val inlineMedia = rememberThreadInlineMediaState(thread.key.toString())
    var scrollTarget by remember(thread.key) { mutableStateOf<String?>(null) }
    var downloadSheet by rememberSaveable(thread.key) { mutableStateOf(false) }
    var pendingPublicDownload by rememberSaveable(thread.key) { mutableStateOf<Boolean?>(null) }
    var initialScrollRestored by rememberSaveable(thread.key) { mutableStateOf(false) }
    var requestedPostUnavailable by remember(thread.key) { mutableStateOf(false) }

    fun downloadMedia(videosOnly: Boolean) {
        if (preferences.askBeforePhotos && !preferences.privateSavedFiles) {
            pendingPublicDownload = videosOnly
        } else {
            viewModel.downloadAllMedia(videosOnly)
        }
    }

    fun saveVisiblePost(flush: Boolean = false) {
        if (layout == ThreadLayout.FILES || rows.isEmpty()) return
        val visibleItem = listState.firstVisibleItemIndex
        val postIndex = (visibleItem - 1).coerceIn(0, rows.lastIndex)
        val offset = if (visibleItem == 0) 0 else listState.firstVisibleItemScrollOffset
        rows[postIndex].id.toLongOrNull()?.let {
            viewModel.saveScrollPosition(
                PostId(it),
                offset,
                rows[postIndex].branchPath,
            )
        }
        if (flush) viewModel.flushScrollPosition()
    }

    fun openMedia(index: Int) {
        saveVisiblePost(flush = true)
        val filter = if (layout == ThreadLayout.FILES) mediaFilter else com.orbin.uinext.components.MediaKindFilter.ALL
        if (onOpenMediaFiltered != null) onOpenMediaFiltered(index, filter) else onOpenMedia(index)
    }

    LaunchedEffect(initialScrollLoaded, initialScrollPosition, rows, initialScrollRestored, initialPostId) {
        val restorePending = initialScrollLoaded && !initialScrollRestored
        val waitingForTarget = rows.isEmpty() || initialPostId != null
        if (!restorePending || waitingForTarget) {
            return@LaunchedEffect
        }
        val saved = initialScrollPosition
        val exactPath =
            if (preferences.autoScrollToLastSeen) {
                memory.rowPath?.let { path ->
                    rows.indexOfFirst {
                        it.branchPath ==
                            path
                    }
                }
                    ?: -1
            } else {
                -1
            }
        val savedTarget =
            if (exactPath >=
                0
            ) {
                exactPath
            } else {
                saved?.let { pos -> rows.indexOfFirst { it.id == pos.postId.value.toString() } } ?: -1
            }
        if (savedTarget >= 0) {
            listState.scrollToItem(savedTarget + 1, saved?.offsetPx?.coerceAtLeast(0) ?: 0)
        }
        initialScrollRestored = true
    }

    LaunchedEffect(initialPostId, rows, layout, initialScrollLoaded) {
        val id = initialPostId ?: return@LaunchedEffect
        if (!initialScrollLoaded || rows.isEmpty()) return@LaunchedEffect
        if (layout == ThreadLayout.FILES) {
            layout = ThreadLayout.POSTS
            return@LaunchedEffect
        }
        val number = id.toString()
        val target = rows.indexOfFirst { it.id == number && !it.preview }
        val missingProjectedTarget = target < 0 && number in presentation.rowsById
        val targetRow = rows.getOrNull(target)
        val targetNeedsReveal =
            targetRow?.let { it.hiddenReason != null || it.branchPath in memory.foldedPaths } == true
        if (missingProjectedTarget || targetNeedsReveal) {
            viewModel.expandToPost(id)
            return@LaunchedEffect
        }
        requestedPostUnavailable = target < 0
        listState.scrollToItem(if (target >= 0) target + 1 else 0)
        initialScrollRestored = true
        if (target >= 0) {
            viewModel.saveScrollPosition(PostId(id), 0, rows[target].branchPath)
            viewModel.flushScrollPosition()
        }
        onInitialPostConsumed()
    }

    LaunchedEffect(mediaScrollIndex, rows, layout, initialScrollRestored, initialPostId) {
        val index = mediaScrollIndex ?: return@LaunchedEffect
        val waitingForPosts = !initialScrollRestored || layout == ThreadLayout.FILES
        val waitingForTarget = rows.isEmpty() || initialPostId != null
        if (waitingForPosts || waitingForTarget) {
            return@LaunchedEffect
        }

        val targetPostId = presentation.postIdForMediaIndex(index)
        val target = targetPostId?.let { postId -> rows.indexOfFirst { it.id == postId.value.toString() } } ?: -1
        if (target < 0 && targetPostId != null && targetPostId.value.toString() in presentation.rowsById) {
            viewModel.expandToPost(targetPostId.value)
            return@LaunchedEffect
        }
        if (target >= 0) {
            rows[target].id.toLongOrNull()?.let(viewModel::expandToPost)
            listState.scrollToItem(target + 1)
            rows[target].id.toLongOrNull()?.let { viewModel.saveScrollPosition(PostId(it), 0, rows[target].branchPath) }
            viewModel.flushScrollPosition()
        }
        onMediaScrollConsumed()
    }

    LaunchedEffect(listState, rows, layout, initialScrollRestored) {
        if (layout == ThreadLayout.FILES || rows.isEmpty() || !initialScrollRestored) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest {
                delay(SCROLL_TRACK_SETTLE_MS)
                saveVisiblePost()
            }
    }

    DisposableEffect(thread.key, layout, rows) {
        onDispose { saveVisiblePost(flush = true) }
    }

    val collapsedSet = memory.foldedPaths
    // The memory re-emits whenever posts are marked read, and this screen also recomposes each time
    // the reading position is saved, both of which happen as the reader scrolls. Recomputing the
    // unread set at every one of those passes walked every post on the main thread.
    val unreadPostIds =
        remember(presentation, memory.seenPostIds) {
            presentation.unseenPostIds(memory.seenPostIds).mapTo(mutableSetOf()) { it.toString() }
        }

    NextPullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ThreadScreen(
                subject = thread.subject ?: viewModel.title,
                board = boardLabel(thread.key.board.value),
                siteName = thread.key.provider.value,
                onBack = onBack,
                posts = rows,
                allPosts = detailRows,
                externalPostId = initialPostId?.toString(),
                onHidePost = { it.id.toLongOrNull()?.let(viewModel::toggleHiddenPost) },
                onHidePosterId = { it.posterId?.let(viewModel::toggleHiddenPoster) },
                onRevealPost = { it.id.toLongOrNull()?.let(viewModel::revealPost) },
                onVisiblePosts = viewModel::markVisible,
                onMarkAllRead = viewModel::markAllRead,
                unreadPostIds = unreadPostIds,
                onMediaFilterChange = { mediaFilter = it },
                onAddPostFilter = { post, target ->
                    val pattern =
                        when (target) {
                            com.orbin.uinext.screens.PostFilterTarget.TEXT -> post.body.take(80)
                            com.orbin.uinext.screens.PostFilterTarget.NAME -> post.posterName.orEmpty()
                            com.orbin.uinext.screens.PostFilterTarget.TRIPCODE -> post.posterTripcode.orEmpty()
                            com.orbin.uinext.screens.PostFilterTarget.COUNTRY -> post.posterCountry.orEmpty()
                        }
                    viewModel.addPostFilter(PostFilterKind.valueOf(target.name), pattern)
                },
                subtitle = statusText,
                statusText = statusText,
                reloading = isRefreshing,
                watching = isWatching,
                saved = isSaved,
                onToggleSaved = viewModel::toggleBookmark,
                layout = layout,
                onLayoutChange = {
                    if (layout != ThreadLayout.FILES) saveVisiblePost(flush = true)
                    layout = it
                },
                files = visibleFiles,
                onOpenFile = { cell -> presentation.mediaIndex[cell.id]?.let(::openMedia) },
                fileTile = { cell, tileModifier ->
                    presentation.attachmentsById[cell.id]?.let { attachment ->
                        MediaThumbnail(
                            attachment = attachment.copy(isSpoiler = false),
                            fullResolution = false,
                            modifier = tileModifier,
                            // Clover album previews have square corners.
                            cornerRadius = 0.dp,
                            onClick = { presentation.mediaIndex[cell.id]?.let(::openMedia) },
                        )
                    }
                },
                collapsed = collapsedSet,
                onToggleCollapse = { post ->
                    haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    viewModel.toggleFold(post.branchPath)
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
                    downloadSheet = true
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
                                id.toLongOrNull()?.let(viewModel::expandToPost)
                                scrollTarget = id
                            },
                            onLinkClick = { link -> uriHandler.openUri(link) },
                        )
                    }
                },
                media = { row, tileModifier ->
                    presentation.rowsById[row.id]?.post?.attachments?.firstOrNull()?.let { attachment ->
                        ThreadInlineMedia(
                            attachment = attachment.copy(isSpoiler = false),
                            identity = "${thread.key}/${row.stableKey}",
                            state = inlineMedia,
                            playback = preferences.playbackPreferences.resolve(thread.key.provider, thread.key.board),
                            onOpenFullscreen = { presentation.firstMediaIndex(row.id)?.let(::openMedia) },
                            modifier = tileModifier,
                            fullResolution =
                                shouldLoadSharpPreview(
                                    modeWantsSharp = true,
                                    setting = preferences.sharpPreviews,
                                    isMetered = isMetered,
                                    isDataSaver = isDataSaver,
                                    mediaType = attachment.type,
                                    sizeBytes = attachment.sizeBytes,
                                ),
                        )
                    }
                },
                onReload = viewModel::refresh,
                onOpenBrowser =
                    viewModel.threadWebUrl?.let { url ->
                        {
                            uriHandler.openUri(url)
                        }
                    },
                onPostReference = { id ->
                    id.toLongOrNull()?.let(viewModel::expandToPost)
                    scrollTarget = id
                },
                wideWindow = wideWindow,
            )
            state.refreshFailure?.let { failure ->
                ReaderRefreshFailure(
                    cause = failure.readerCause(),
                    onRetry = viewModel::refresh,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            if (state.refreshFailure == null && requestedPostUnavailable) {
                ReaderRefreshFailure(
                    cause = ReaderFailureCause.NOT_FOUND,
                    message = stringResource(R.string.thread_requested_post_unavailable),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            NextSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            if (downloadSheet) {
                AlertDialog(
                    onDismissRequest = { downloadSheet = false },
                    title = { Text(stringResource(R.string.thread_download_title)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                downloadSheet = false
                                downloadMedia(videosOnly = true)
                            },
                        ) { Text(stringResource(R.string.thread_download_videos)) }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                downloadSheet = false
                                downloadMedia(videosOnly = false)
                            },
                        ) { Text(stringResource(R.string.thread_download_everything)) }
                    },
                )
            }
            pendingPublicDownload?.let { videosOnly ->
                NextConfirmDialog(
                    title = stringResource(R.string.thread_public_save_title),
                    message = stringResource(R.string.thread_public_save_message),
                    confirmLabel = stringResource(R.string.thread_public_save_confirm),
                    destructive = false,
                    onDismiss = { pendingPublicDownload = null },
                    onConfirm = {
                        pendingPublicDownload = null
                        viewModel.downloadAllMedia(videosOnly)
                    },
                )
            }
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
