package com.orbin.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.orbin.core.model.AppSettings
import com.orbin.core.model.PostFilterKind
import com.orbin.core.model.PostFilterRule
import com.orbin.core.model.ThreadMemory
import com.orbin.core.model.isWideWindow
import com.orbin.core.reader.ReaderPostComment
import com.orbin.core.reader.ThreadPresentationIndex
import com.orbin.core.reader.readerRows
import com.orbin.core.reader.readerThreadStatus
import com.orbin.core.reader.unseenPostIds
import com.orbin.core.reader.visibleMediaIndices
import com.orbin.domain.repository.InMemoryThreadMemoryRepository
import com.orbin.domain.repository.ThreadMemoryRepository
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_media_close
import com.orbin.ios.resources.ios_media_loading
import com.orbin.ios.resources.ios_media_viewer
import com.orbin.ios.resources.ios_pencil_download_all
import com.orbin.ios.resources.ios_pencil_share
import com.orbin.ios.resources.ios_pencil_unwatch
import com.orbin.ios.resources.ios_pencil_watch
import com.orbin.ios.resources.ios_reader_thread
import com.orbin.ios.resources.ios_saved_cancel
import com.orbin.ios.resources.ios_saved_continue
import com.orbin.ios.resources.ios_saved_export_confirm
import com.orbin.ios.resources.ios_saved_export_title
import com.orbin.ios.resources.ios_thread_offline_copy
import com.orbin.ios.resources.ios_thread_requested_post_unavailable
import com.orbin.uinext.components.NextConfirmDialog
import com.orbin.uinext.components.NextPullToRefresh
import com.orbin.uinext.components.ReaderAppBar
import com.orbin.uinext.components.ReaderMediaClose
import com.orbin.uinext.components.ReaderMediaTitle
import com.orbin.uinext.components.ReaderMediaToolbar
import com.orbin.uinext.components.ReaderNavigation
import com.orbin.uinext.components.reader
import com.orbin.uinext.components.rememberThreadInlineMediaState
import com.orbin.uinext.screens.ReaderErrorState
import com.orbin.uinext.screens.ReaderFailureCause
import com.orbin.uinext.screens.ReaderRefreshFailure
import com.orbin.uinext.screens.ReaderThreadSkeleton
import com.orbin.uinext.screens.ThreadLayout
import com.orbin.uinext.screens.ThreadScreen
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

// A thread and the full-screen viewer over its files.

@Composable
internal fun ThreadDestination(
    browser: Browser,
    downloads: MediaDownloads,
    pages: ThreadPages?,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val thread by browser.thread.collectAsState()
    val presentation by browser.preparedThread.collectAsState()
    val refreshing by browser.threadRefreshing.collectAsState()
    val refreshFailure by browser.threadRefreshFailure.collectAsState()
    val mediaTarget by browser.mediaScroll.target.collectAsState()
    val initialPostTarget by browser.pendingThreadPost.collectAsState()
    val preferences by browser.settings.current.collectAsState()
    var pendingPublicSave by remember { mutableStateOf<(() -> Unit)?>(null) }
    if (pendingPublicSave != null) {
        NextConfirmDialog(
            title = stringResource(Res.string.ios_saved_export_title),
            message = stringResource(Res.string.ios_saved_export_confirm),
            confirmLabel = stringResource(Res.string.ios_saved_continue),
            dismissLabel = stringResource(Res.string.ios_saved_cancel),
            destructive = false,
            onDismiss = { pendingPublicSave = null },
            onConfirm = {
                val save = pendingPublicSave
                pendingPublicSave = null
                save?.invoke()
            },
        )
    }
    NextPullToRefresh(
        isRefreshing = refreshing,
        onRefresh = browser::retry,
        modifier = Modifier.fillMaxSize(),
    ) {
        val loaded = (thread as? Load.Ready)?.value
        val prepared = presentation?.takeIf { it.thread == loaded }
        if (loaded == null || prepared == null) {
            Column(Modifier.fillMaxSize()) {
                ReaderAppBar(
                    title = stringResource(Res.string.ios_reader_thread),
                    navigation = ReaderNavigation.BACK,
                    onNavigation = browser::back,
                )
                when (val state = thread) {
                    is Load.Failed ->
                        ReaderErrorState(
                            cause = state.cause.toReaderCause(),
                            onRetry = browser::retry,
                            message = state.message.takeIf { state.cause == BrowserFailureCause.UNKNOWN },
                        )
                    else -> ReaderThreadSkeleton(wideWindow = readerWideWindow())
                }
            }
        } else {
            val watching by remember(loaded.key) { browser.watched.watching(loaded.key) }.collectAsState(false)
            val saved by remember(loaded.key) { browser.saved(loaded.key) }.collectAsState(false)
            ThreadContent(
                memoryRepository = browser.threadMemory,
                preferences = preferences,
                onAddFilter = { rule ->
                    browser.settings.setPostFilterRules(
                        (
                            browser.settings.current.value.postFilterRules +
                                rule
                        ).distinct(),
                    )
                },
                onAllRead = { browser.markThreadRead(loaded.key) },
                onOpenFilteredFile = { index, filter -> browser.openMedia(index, filter) },
                presentation = prepared,
                refreshing = refreshing,
                refreshFailure = refreshFailure,
                watching = watching,
                saved = saved,
                onToggleSaved = { browser.toggleSaved(loaded) },
                onWatch = { browser.watched.toggle(loaded) },
                onBack = { browser.back() },
                onReload = browser::retry,
                onOpenBrowser =
                    browser.threadWebUrl(loaded.key)?.let { url ->
                        { safeExternalLink(url)?.let(uriHandler::openUri) }
                    },
                onOpenFile = browser::openMedia,
                mediaPostId = mediaTarget?.takeIf { it.first == loaded.key }?.second,
                onMediaScrollConsumed = { mediaTarget?.let(browser.mediaScroll::consume) },
                initialPostId =
                    initialPostTarget
                        ?.takeIf { it.first == loaded.key }
                        ?.second
                        ?.value
                        ?.toString(),
                onInitialPostConsumed = { initialPostTarget?.let(browser::clearPendingThreadPost) },
                onDownloadFiles = { indices ->
                    val files =
                        indices.mapNotNull { index ->
                            prepared.attachments.getOrNull(index)?.let { index to it }
                        }
                    val save: () -> Unit = {
                        files.forEach { (index, file) ->
                            downloads.save(
                                file,
                                loaded.key,
                                loaded.subject,
                                privateTarget = preferences.privateSavedFiles,
                                postId = prepared.postIdForMediaIndex(index)?.value,
                            )
                        }
                    }
                    val asksForPublicSave = preferences.askBeforePhotos && !preferences.privateSavedFiles
                    val includesPhotos = files.any { saveTargetOf(it.second.originalFileName) != SaveTarget.FILE }
                    if (asksForPublicSave && includesPhotos) {
                        pendingPublicSave = save
                    } else {
                        save()
                    }
                },
                onSavePage = pages?.let { { scope.launch { it.save(loaded) } } },
                onShare = {
                    val fallback = "/${loaded.key.board.value}/${loaded.key.thread.value}"
                    val shareText = loaded.subject?.takeIf(String::isNotBlank) ?: fallback
                    shareContent(text = shareText, url = browser.threadWebUrl(loaded.key))
                },
            )
        }
    }
}

@Composable
internal fun ThreadContent(
    presentation: ThreadPresentationIndex,
    refreshing: Boolean,
    refreshFailure: Load.Failed?,
    watching: Boolean,
    onWatch: () -> Unit,
    onOpenFile: (index: Int) -> Unit,
    mediaPostId: String? = null,
    onMediaScrollConsumed: () -> Unit = {},
    onDownloadAll: () -> Unit = {},
    onSavePage: (() -> Unit)? = null,
    onShare: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    saved: Boolean = false,
    onToggleSaved: (() -> Unit)? = null,
    onReload: (() -> Unit)? = null,
    onOpenBrowser: (() -> Unit)? = null,
    memoryRepository: ThreadMemoryRepository? = null,
    preferences: AppSettings = AppSettings.Default,
    onAddFilter: ((PostFilterRule) -> Unit)? = null,
    onAllRead: () -> Unit = {},
    onOpenFilteredFile: ((Int, com.orbin.uinext.components.MediaKindFilter) -> Unit)? = null,
    onDownloadFiles: ((List<Int>) -> Unit)? = null,
    initialPostId: String? = null,
    onInitialPostConsumed: () -> Unit = {},
) {
    val thread = presentation.thread
    val statusText = readerThreadStatus(presentation)
    // The window's size, not this pane's, decides the thumbnail size: the thread pane of a split
    // is narrower than the window it is in.
    val wideWindow = readerWideWindow()
    val memories = memoryRepository ?: remember { InMemoryThreadMemoryRepository() }
    val observedMemory by remember(thread.key, memories) { memories.observe(thread.key) }.collectAsState(null)
    val memory = observedMemory ?: ThreadMemory(thread.key)
    var initialMemory by remember(thread.key) { mutableStateOf<ThreadMemory?>(null) }
    var revealed by remember(thread.key) { mutableStateOf<Set<Long>>(emptySet()) }
    val scope = rememberCoroutineScope()
    val posts by produceState<List<com.orbin.uinext.screens.Post>>(
        emptyList(),
        observedMemory != null,
        presentation,
        memory.foldedPaths,
        memory.hiddenPostIds,
        memory.hiddenPosterIds,
        memory.seenPostIds,
        preferences.threadLayout,
        preferences.postFilterRules,
        revealed,
    ) {
        if (observedMemory == null) return@produceState
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
    val detailPosts by produceState<List<com.orbin.uinext.screens.Post>>(
        emptyList(),
        observedMemory != null,
        presentation,
        memory.hiddenPostIds,
        memory.hiddenPosterIds,
        memory.seenPostIds,
        preferences.postFilterRules,
        revealed,
    ) {
        if (observedMemory == null) return@produceState
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
    LaunchedEffect(thread.key, memories) { initialMemory = memories.get(thread.key) }
    LaunchedEffect(thread, presentation.fromSavedCopy) {
        val known = thread.allPosts.mapTo(mutableSetOf()) { it.id.value }
        memories.update(thread.key) {
            if (presentation.fromSavedCopy) {
                it.copy(
                    knownPostIds = it.knownPostIds + known,
                    seenPostIds = it.seenPostIds + thread.originalPost.id.value,
                )
            } else {
                it.copy(
                    knownPostIds = known,
                    seenPostIds = (it.seenPostIds intersect known) + thread.originalPost.id.value,
                )
            }
        }
    }

    fun updateMemory(transform: (ThreadMemory) -> ThreadMemory) {
        scope.launch { memories.update(thread.key, transform) }
    }

    fun expandPost(id: String) {
        val number = id.toLongOrNull() ?: return
        revealed = revealed + number
        val row =
            presentation
                .readerRows(nested = preferences.threadLayout == com.orbin.core.model.ThreadLayout.NESTED)
                .firstOrNull { it.id == id && !it.preview } ?: return
        updateMemory { it.copy(foldedPaths = it.foldedPaths - row.ancestorPaths.toSet() - row.branchPath) }
    }
    val byId = presentation.rowsById
    // The memory re-emits each time the reading position is saved (180ms after every scroll) and
    // whenever posts are marked read. Both values walk every post, so they are kept per change of
    // what has been read rather than recomputed on the main thread at each of those emissions.
    val unreadPostIds =
        remember(presentation, memory.seenPostIds) {
            presentation.unseenPostIds(memory.seenPostIds).mapTo(mutableSetOf()) { it.toString() }
        }
    val firstUnreadPostId =
        remember(presentation, memory.seenPostIds) {
            presentation.thread.replies
                .firstOrNull { it.id.value !in memory.seenPostIds }
                ?.id
                ?.value
                ?.toString()
        }
    val boardLabel = "/${thread.key.board.value}/"
    val visibleOwners =
        remember(posts, memory.foldedPaths) {
            posts
                .filter {
                    it.hiddenReason == null && !it.preview && it.branchPath !in memory.foldedPaths
                }.mapTo(mutableSetOf()) { it.id }
        }
    val fileCells =
        remember(presentation, visibleOwners) {
            presentation.fileCells.filterIndexed { index, _ ->
                presentation.postIdForMediaIndex(index)?.value?.toString() in
                    visibleOwners
            }
        }
    val attachmentsById = presentation.attachmentsById
    val uriHandler = LocalUriHandler.current
    // Where a tapped quote asks the list to scroll; cleared once the screen has scrolled there.
    var scrollTarget by remember(thread) { mutableStateOf<String?>(null) }
    // Posts / Files chrome and collapse state — same wiring as Android's NextThreadScreen.
    var layout by rememberSaveable(thread.key) { mutableStateOf(ThreadLayout.POSTS) }
    var mediaFilter by rememberSaveable(thread.key) { mutableStateOf(com.orbin.uinext.components.MediaKindFilter.ALL) }
    val downloadVisibleFiles = {
        if (onDownloadFiles == null) {
            onDownloadAll()
        } else {
            val cells = if (layout == ThreadLayout.FILES) fileCells.filter { mediaFilter.shows(it.kind) } else fileCells
            onDownloadFiles(cells.mapNotNull { presentation.mediaIndex[it.id] })
        }
    }

    fun openFile(index: Int) {
        val filter = if (layout == ThreadLayout.FILES) mediaFilter else com.orbin.uinext.components.MediaKindFilter.ALL
        if (onOpenFilteredFile != null) onOpenFilteredFile(index, filter) else onOpenFile(index)
    }
    val listState = rememberLazyListState()
    val inlineMedia = rememberThreadInlineMediaState(thread.key.toString())
    var restored by remember(thread.key) { mutableStateOf(false) }
    var requestedPostUnavailable by remember(thread.key) { mutableStateOf(false) }
    LaunchedEffect(initialMemory, posts, initialPostId) {
        val anchor = initialMemory ?: return@LaunchedEffect
        if (restored || posts.isEmpty() || initialPostId != null) return@LaunchedEffect
        if (preferences.autoScrollToLastSeen && mediaPostId == null) {
            val pathIndex = posts.indexOfFirst { it.branchPath == anchor.rowPath }
            val index =
                if (pathIndex >=
                    0
                ) {
                    pathIndex
                } else {
                    posts.indexOfFirst { it.id == anchor.anchorPostId?.toString() }
                }
            if (index >= 0) listState.scrollToItem(index + 1, anchor.offsetPx.coerceAtLeast(0))
        }
        restored = true
    }
    LaunchedEffect(initialPostId, posts, layout) {
        val id = initialPostId ?: return@LaunchedEffect
        if (posts.isEmpty()) return@LaunchedEffect
        if (layout == ThreadLayout.FILES) {
            layout = ThreadLayout.POSTS
            return@LaunchedEffect
        }
        val target = posts.indexOfFirst { it.id == id && !it.preview }
        val missingProjectedTarget = target < 0 && id in byId
        val targetRow = posts.getOrNull(target)
        val targetNeedsReveal =
            targetRow?.let { it.hiddenReason != null || it.branchPath in memory.foldedPaths } == true
        if (missingProjectedTarget || targetNeedsReveal) {
            expandPost(id)
            return@LaunchedEffect
        }
        requestedPostUnavailable = target < 0
        listState.scrollToItem(if (target >= 0) target + 1 else 0)
        restored = true
        onInitialPostConsumed()
    }
    val latestPosts by rememberUpdatedState(posts)

    fun anchor(): Triple<Long, String, Int>? {
        if (layout == ThreadLayout.FILES) return null
        val item = listState.firstVisibleItemIndex
        val row = latestPosts.getOrNull((item - 1).coerceAtLeast(0)) ?: return null
        val offset = if (item == 0) 0 else listState.firstVisibleItemScrollOffset
        return row.id.toLongOrNull()?.let { Triple(it, row.branchPath, offset) }
    }
    LaunchedEffect(listState, restored) {
        if (!restored) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collectLatest {
            delay(180)
            anchor()?.let { (id, path, offset) ->
                memories.update(thread.key) { it.copy(anchorPostId = id, rowPath = path, offsetPx = offset) }
            }
        }
    }
    DisposableEffect(thread.key, listState) {
        onDispose {
            anchor()?.let { (id, path, offset) ->
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    withContext(NonCancellable) {
                        memories.update(thread.key) { it.copy(anchorPostId = id, rowPath = path, offsetPx = offset) }
                    }
                }
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestThread by rememberUpdatedState(thread)
    val latestRefreshing by rememberUpdatedState(refreshing)
    val latestFailure by rememberUpdatedState(refreshFailure)
    val latestSavedCopy by rememberUpdatedState(presentation.fromSavedCopy)
    val reload by rememberUpdatedState(onReload)
    LaunchedEffect(thread.key, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var interval = 8_000L
            while (currentCoroutineContext().isActive) {
                delay(interval)
                val copyOrClosed = latestThread.stats.isArchived || latestThread.stats.isClosed || latestSavedCopy
                val noLongerAvailable = latestFailure?.cause == BrowserFailureCause.NOT_FOUND
                if (copyOrClosed || noLongerAvailable) {
                    return@repeatOnLifecycle
                }
                if (latestRefreshing) continue
                val before = latestThread.allPosts.size
                reload?.invoke()
                delay(250)
                while (latestRefreshing) delay(100)
                interval = if (latestThread.allPosts.size > before) 8_000L else (interval * 2).coerceAtMost(60_000L)
            }
        }
    }
    LaunchedEffect(mediaPostId, posts, layout, restored, initialPostId) {
        val waitingForPosts = layout == ThreadLayout.FILES || posts.isEmpty() || !restored
        val waitingForTarget = mediaPostId == null || initialPostId != null
        if (waitingForPosts || waitingForTarget) {
            return@LaunchedEffect
        }
        val index = posts.indexOfFirst { it.id == mediaPostId }
        if (index < 0 && mediaPostId in byId) {
            expandPost(mediaPostId)
            return@LaunchedEffect
        }
        // Item zero is the thread header. Files mode keeps its position until Posts is selected.
        if (index >= 0) {
            expandPost(mediaPostId)
            listState.scrollToItem(index + 1)
        }
        onMediaScrollConsumed()
    }
    var pencilMenu by remember { mutableStateOf(false) }
    val rollScroll = with(LocalDensity.current) { PENCIL_ROLL_SCROLL.toPx() }
    // Apple Pencil: a double-tap steps to the next post, turning the barrel scrolls, and a squeeze
    // offers what can be done with the whole thread.
    OnPencilGesture { gesture ->
        when (gesture) {
            PencilGesture.DOUBLE_TAP -> listState.animateScrollToItem(listState.firstVisibleItemIndex + 1)
            PencilGesture.SQUEEZE -> pencilMenu = true
        }
    }
    OnPencilRoll { turn -> listState.scrollBy(turn * rollScroll) }
    Box(Modifier.fillMaxSize()) {
        ThreadScreen(
            listState = listState,
            subject = thread.subject?.takeIf { it.isNotBlank() } ?: "No.${thread.key.thread.value}",
            board = boardLabel,
            siteName = thread.key.provider.value,
            onBack = onBack,
            posts = posts,
            allPosts = detailPosts,
            externalPostId = initialPostId,
            onMediaFilterChange = { mediaFilter = it },
            onHidePost = { post ->
                post.id.toLongOrNull()?.let { id ->
                    revealed = revealed - id
                    updateMemory {
                        it.copy(
                            hiddenPostIds =
                                if (id in
                                    it.hiddenPostIds
                                ) {
                                    it.hiddenPostIds - id
                                } else {
                                    it.hiddenPostIds + id
                                },
                        )
                    }
                }
            },
            onHidePosterId = { post ->
                post.posterId?.let { id ->
                    updateMemory {
                        it.copy(
                            hiddenPosterIds =
                                if (id in
                                    it.hiddenPosterIds
                                ) {
                                    it.hiddenPosterIds - id
                                } else {
                                    it.hiddenPosterIds + id
                                },
                        )
                    }
                }
            },
            onRevealPost = { post -> post.id.toLongOrNull()?.let { revealed = revealed + it } },
            onAddPostFilter = { post, target ->
                val pattern =
                    when (target) {
                        com.orbin.uinext.screens.PostFilterTarget.TEXT -> post.body.take(80)
                        com.orbin.uinext.screens.PostFilterTarget.NAME -> post.posterName.orEmpty()
                        com.orbin.uinext.screens.PostFilterTarget.TRIPCODE -> post.posterTripcode.orEmpty()
                        com.orbin.uinext.screens.PostFilterTarget.COUNTRY -> post.posterCountry.orEmpty()
                    }
                if (pattern.isNotBlank()) {
                    onAddFilter?.invoke(
                        PostFilterRule(PostFilterKind.valueOf(target.name), pattern),
                    )
                }
            },
            onVisiblePosts = { ids ->
                val seen = ids.mapNotNull(String::toLongOrNull).toSet()
                if (!memory.seenPostIds.containsAll(seen)) {
                    scope.launch {
                        memories.update(thread.key) {
                            it.copy(
                                seenPostIds =
                                    it.seenPostIds + (seen intersect it.knownPostIds),
                            )
                        }
                        val current = memories.get(thread.key)
                        val allKnownSeen =
                            current.knownPostIds.isNotEmpty() && current.seenPostIds.containsAll(current.knownPostIds)
                        val persistentLiveRead = !preferences.historySessionOnly && !presentation.fromSavedCopy
                        if (persistentLiveRead && allKnownSeen) {
                            onAllRead()
                        }
                    }
                }
            },
            onMarkAllRead = {
                updateMemory {
                    it.copy(
                        seenPostIds = it.knownPostIds,
                        acknowledgedReplyCount = maxOf(it.acknowledgedReplyCount, thread.stats.replyCount),
                    )
                }
                onAllRead()
            },
            unreadPostIds = unreadPostIds,
            subtitle = statusText,
            statusText = statusText,
            reloading = refreshing,
            watching = watching,
            saved = saved,
            onToggleSaved = onToggleSaved,
            layout = layout,
            onLayoutChange = { layout = it },
            files = fileCells,
            onOpenFile = { cell ->
                presentation.mediaIndex[cell.id]?.let(::openFile)
            },
            fileTile = { cell, tileModifier ->
                attachmentsById[cell.id]?.let { attachment ->
                    Box(tileModifier) {
                        ReaderThumbnail(
                            attachment = attachment.copy(isSpoiler = false),
                            contentDescription = attachment.originalFileName,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            },
            collapsed = memory.foldedPaths,
            onToggleCollapse = { post ->
                updateMemory {
                    it.copy(
                        foldedPaths =
                            if (post.branchPath in
                                it.foldedPaths
                            ) {
                                it.foldedPaths - post.branchPath
                            } else {
                                it.foldedPaths + post.branchPath
                            },
                    )
                }
            },
            onWatch = onWatch,
            onDownloadAll = downloadVisibleFiles,
            onSavePage = onSavePage,
            onShare = onShare,
            firstUnreadPostId = firstUnreadPostId,
            scrollToPostId = scrollTarget,
            onScrollConsumed = { scrollTarget = null },
            body = { post ->
                byId[post.id]?.let { entry ->
                    ReaderPostComment(
                        row = entry,
                        onQuoteClick = { target ->
                            val id = target.value.toString()
                            expandPost(id)
                            scrollTarget = id
                        },
                        onLinkClick = { url -> safeExternalLink(url)?.let(uriHandler::openUri) },
                    )
                }
            },
            media = { post, modifier ->
                byId[post.id]?.post?.attachments?.firstOrNull()?.let { attachment ->
                    ThreadInlineMedia(
                        attachment = attachment.copy(isSpoiler = false),
                        identity = "${thread.key}/${post.stableKey}",
                        state = inlineMedia,
                        playback = preferences.playbackPreferences.resolve(thread.key.provider, thread.key.board),
                        onOpenFullscreen = { presentation.firstMediaIndex(post.id)?.let(::openFile) },
                        modifier = modifier,
                    )
                }
            },
            onReload = onReload,
            onOpenBrowser = onOpenBrowser,
            onPostReference = { id ->
                expandPost(id)
                scrollTarget = id
            },
            wideWindow = wideWindow,
        )
        refreshFailure?.let { failure ->
            ReaderRefreshFailure(
                cause = failure.cause.toReaderCause(),
                onRetry = onReload,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (refreshFailure == null && presentation.fromSavedCopy) {
            ReaderRefreshFailure(
                cause = ReaderFailureCause.NETWORK,
                message = stringResource(Res.string.ios_thread_offline_copy),
                onRetry = onReload,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (refreshFailure == null && !presentation.fromSavedCopy && requestedPostUnavailable) {
            ReaderRefreshFailure(
                cause = ReaderFailureCause.NOT_FOUND,
                message = stringResource(Res.string.ios_thread_requested_post_unavailable),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        val watchLabel = if (watching) Res.string.ios_pencil_unwatch else Res.string.ios_pencil_watch
        PencilMenu(
            expanded = pencilMenu,
            onDismiss = { pencilMenu = false },
            actions =
                listOf<Pair<String, () -> Unit>>(
                    stringResource(Res.string.ios_pencil_download_all) to downloadVisibleFiles,
                    stringResource(watchLabel) to onWatch,
                    stringResource(Res.string.ios_pencil_share) to onShare,
                ),
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** How far one radian of barrel roll scrolls a thread. */
private val PENCIL_ROLL_SCROLL = 400.dp

@Composable
private fun readerWideWindow(): Boolean {
    val windowSize = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) {
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
}

@Composable
internal fun MediaDestination(
    browser: Browser,
    downloads: MediaDownloads,
    route: Route.Media,
) {
    val thread by browser.thread.collectAsState()
    val presentation by browser.preparedThread.collectAsState()
    val settings by browser.settings.current.collectAsState()
    val memory by remember(browser, route.thread) { browser.threadMemory.observe(route.thread) }
        .collectAsState(ThreadMemory(route.thread))
    val open = (thread as? Load.Ready)?.value?.takeIf { it.key == route.thread }
    val prepared = presentation?.takeIf { it.thread === open }
    val originalLoadedFiles = prepared?.attachments.orEmpty()
    val requestedIndex = remember(route, originalLoadedFiles) { route.startIndexIn(originalLoadedFiles) }
    // A deliberate reveal/open may show its selected owner, never every other hidden poster.
    val requestedOwner = requestedIndex?.let { prepared?.postIdForMediaIndex(it)?.value }
    val allowedIndices =
        remember(prepared, settings, memory, requestedOwner) {
            prepared?.visibleMediaIndices(settings, memory, setOfNotNull(requestedOwner)).orEmpty().toSet()
        }
    val loadedFiles =
        remember(originalLoadedFiles, allowedIndices) {
            originalLoadedFiles.filterIndexed { index, _ -> index in allowedIndices }
        }
    val contentBlocked = (thread as? Load.Failed)?.contentBlocked == true
    var seedDenied by rememberSaveable(route) { mutableStateOf(contentBlocked) }
    SideEffect { if (contentBlocked) seedDenied = true }
    val files =
        remember(
            route,
            loadedFiles,
            contentBlocked,
            seedDenied,
        ) { if (contentBlocked || seedDenied) loadedFiles else route.viewerFiles(loadedFiles) }
    val requestedFile = requestedIndex?.let(originalLoadedFiles::getOrNull)
    val startIndex =
        remember(route, files, requestedFile) {
            if (requestedFile != null) files.indexOfViewerFile(requestedFile) else route.startIndexIn(files)
        }
    if (startIndex == null) {
        // The viewer closes an empty playlist. Keep loading/error presentation outside it so a
        // direct catalog entry cannot dismiss itself before its thread has arrived.
        val loadingLabel = stringResource(Res.string.ios_media_loading)
        // Keep the status-bar ground black under the translucent viewer toolbar in every state.
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            ReaderMediaToolbar {
                ReaderMediaClose(stringResource(Res.string.ios_media_close)) { browser.back() }
                ReaderMediaTitle(stringResource(Res.string.ios_media_viewer), "", Modifier.weight(1f))
            }
            when (val state = thread) {
                is Load.Failed ->
                    ReaderErrorState(
                        cause = state.cause.toReaderCause(),
                        onRetry = browser::retry,
                        message = state.message.takeIf { state.cause == BrowserFailureCause.UNKNOWN },
                    )
                is Load.Ready -> ReaderErrorState(ReaderFailureCause.NOT_FOUND, onRetry = browser::retry)
                else ->
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = reader.accent,
                            modifier = Modifier.semantics { contentDescription = loadingLabel },
                        )
                    }
            }
        }
        return
    }
    key(route) {
        MediaViewer(
            playback = settings.playbackPreferences.resolve(route.thread.provider, route.thread.board),
            askBeforePhotos = settings.askBeforePhotos && !settings.privateSavedFiles,
            autoAdvance = settings.fullscreenAutoAdvance,
            onAutoAdvanceChange = { browser.settings.setFullscreenAutoAdvance(it) },
            initialFilter = route.mediaFilter,
            files = files,
            startIndex = startIndex,
            onPageChanged = { index ->
                files.getOrNull(index)?.let { selected ->
                    originalLoadedFiles.indexOfViewerFile(selected)?.let { browser.mediaScroll.select(route, it) }
                }
            },
            onClose = { browser.back() },
            onSave = { file -> downloads.save(file, route.thread, open?.subject) },
            onDownloadRest =
                if (files.size > 1) {
                    { current ->
                        files.forEachIndexed { index, file ->
                            if (index != current) downloads.save(file, route.thread, open?.subject)
                        }
                    }
                } else {
                    null
                },
        )
    }
}
