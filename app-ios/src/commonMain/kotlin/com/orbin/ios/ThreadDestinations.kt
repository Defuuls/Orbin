package com.orbin.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbin.core.model.isWideWindow
import com.orbin.core.reader.ReaderPostComment
import com.orbin.core.reader.ThreadPresentationIndex
import com.orbin.core.reader.readerThreadStatus
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_media_close
import com.orbin.ios.resources.ios_media_loading
import com.orbin.ios.resources.ios_media_viewer
import com.orbin.ios.resources.ios_pencil_download_all
import com.orbin.ios.resources.ios_pencil_share
import com.orbin.ios.resources.ios_pencil_unwatch
import com.orbin.ios.resources.ios_pencil_watch
import com.orbin.ios.resources.ios_reader_thread
import com.orbin.uinext.NextPullToRefresh
import com.orbin.uinext.ReaderAppBar
import com.orbin.uinext.ReaderErrorState
import com.orbin.uinext.ReaderFailureCause
import com.orbin.uinext.ReaderMediaClose
import com.orbin.uinext.ReaderMediaTitle
import com.orbin.uinext.ReaderMediaToolbar
import com.orbin.uinext.ReaderNavigation
import com.orbin.uinext.ReaderRefreshFailure
import com.orbin.uinext.ReaderThreadSkeleton
import com.orbin.uinext.ThreadLayout
import com.orbin.uinext.ThreadScreen
import com.orbin.uinext.reader
import kotlinx.coroutines.launch
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
    val firstUnread by browser.firstUnreadPostId.collectAsState()
    val mediaTarget by browser.mediaScroll.target.collectAsState()
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
                presentation = prepared,
                refreshing = refreshing,
                refreshFailure = refreshFailure,
                watching = watching,
                saved = saved,
                onToggleSaved = { browser.toggleSaved(loaded) },
                firstUnreadPostId = firstUnread,
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
                onDownloadAll = {
                    loaded.files.forEach { file -> downloads.save(file, loaded.key, loaded.subject) }
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
    firstUnreadPostId: String?,
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
) {
    val thread = presentation.thread
    val statusText = readerThreadStatus(presentation)
    // The window's size, not this pane's, decides the thumbnail size: the thread pane of a split
    // is narrower than the window it is in.
    val wideWindow = readerWideWindow()
    val posts = presentation.rowModels
    val byId = presentation.rowsById
    val boardLabel = "/${thread.key.board.value}/"
    val fileCells = presentation.fileCells
    val attachmentsById = presentation.attachmentsById
    val uriHandler = LocalUriHandler.current
    // Where a tapped quote asks the list to scroll; cleared once the screen has scrolled there.
    var scrollTarget by remember(thread) { mutableStateOf<String?>(null) }
    // Posts / Files chrome and collapse state — same wiring as Android's NextThreadScreen.
    var layout by rememberSaveable(thread.key) { mutableStateOf(ThreadLayout.POSTS) }
    val collapsed =
        rememberSaveable(
            thread.key,
            saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
        ) { mutableStateListOf<String>() }
    val listState = rememberLazyListState()
    LaunchedEffect(mediaPostId, posts, layout) {
        if (mediaPostId == null || layout != ThreadLayout.POSTS) return@LaunchedEffect
        val index = posts.indexOfFirst { it.id == mediaPostId }
        // Item zero is the thread header. Files mode keeps its position until Posts is selected.
        if (index >= 0) {
            collapsed.remove(mediaPostId)
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
                presentation.mediaIndex[cell.id]?.let(onOpenFile)
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
            collapsed = collapsed.toSet(),
            onToggleCollapse = { post ->
                if (!collapsed.remove(post.id)) collapsed.add(post.id)
            },
            onWatch = onWatch,
            onDownloadAll = onDownloadAll,
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
                            collapsed.remove(id)
                            scrollTarget = id
                        },
                        onLinkClick = { url -> safeExternalLink(url)?.let(uriHandler::openUri) },
                    )
                }
            },
            media = { post, modifier ->
                // The post's first file, cropped to the square the screen gives it. The viewer
                // pages through the rest. It is the site's thumbnail: a long jump can compose
                // several video posts just before the viewer needs the same decoder resources.
                byId[post.id]?.post?.attachments?.firstOrNull()?.let { attachment ->
                    Box(modifier.clickable { presentation.firstMediaIndex(post.id)?.let(onOpenFile) }) {
                        ReaderThumbnail(
                            attachment = attachment.copy(isSpoiler = false),
                            contentDescription = attachment.originalFileName,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            },
            onReload = onReload,
            onOpenBrowser = onOpenBrowser,
            onPostReference = { id ->
                collapsed.remove(id)
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
        val watchLabel = if (watching) Res.string.ios_pencil_unwatch else Res.string.ios_pencil_watch
        PencilMenu(
            expanded = pencilMenu,
            onDismiss = { pencilMenu = false },
            actions =
                listOf<Pair<String, () -> Unit>>(
                    stringResource(Res.string.ios_pencil_download_all) to onDownloadAll,
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
    val settings by browser.settings.current.collectAsState()
    val open = (thread as? Load.Ready)?.value?.takeIf { it.key == route.thread }
    val files = remember(open) { open?.files.orEmpty() }
    val startIndex = remember(route, files) { route.startIndexIn(files) }
    if (open == null || startIndex == null) {
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
            files = files,
            startIndex = startIndex,
            onPageChanged = { browser.mediaScroll.select(route, it) },
            onClose = { browser.back() },
            onSave = { file -> downloads.save(file, route.thread, open.subject) },
            onDownloadRest =
                if (files.size > 1) {
                    { current ->
                        files.forEachIndexed { index, file ->
                            if (index != current) downloads.save(file, route.thread, open.subject)
                        }
                    }
                } else {
                    null
                },
        )
    }
}
