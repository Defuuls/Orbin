package com.orbin.ios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.orbin.core.model.Thread
import com.orbin.core.ui.post.PostCommentText
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_pencil_download_all
import com.orbin.ios.resources.ios_pencil_share
import com.orbin.ios.resources.ios_pencil_unwatch
import com.orbin.ios.resources.ios_pencil_watch
import com.orbin.uinext.MediaCell
import com.orbin.uinext.NextPullToRefresh
import com.orbin.uinext.ThreadLayout
import com.orbin.uinext.ThreadScreen
import com.orbin.uinext.next
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextType
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

// A thread and the full-screen viewer over its files.

@Composable
internal fun ThreadDestination(
    browser: Browser,
    downloads: MediaDownloads,
    pages: ThreadPages?,
) {
    val scope = rememberCoroutineScope()
    val thread by browser.thread.collectAsState()
    val firstUnread by browser.firstUnreadPostId.collectAsState()
    val mediaTarget by browser.mediaScroll.target.collectAsState()
    NextPullToRefresh(
        isRefreshing = thread is Load.Loading,
        onRefresh = browser::retry,
        modifier = Modifier.fillMaxSize(),
    ) {
        Loaded(thread, onRetry = browser::retry) { loaded ->
            val watching by remember(loaded.key) { browser.watched.watching(loaded.key) }.collectAsState(false)
            val saved by remember(loaded.key) { browser.saved(loaded.key) }.collectAsState(false)
            ThreadContent(
                thread = loaded,
                watching = watching,
                saved = saved,
                onToggleSaved = { browser.toggleSaved(loaded) },
                firstUnreadPostId = firstUnread,
                onWatch = { browser.watched.toggle(loaded) },
                onBack = { browser.back() },
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
private fun ThreadContent(
    thread: Thread,
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
) {
    val now = remember(thread) { Clock.System.now().toEpochMilliseconds() }
    val posts = remember(thread) { thread.toPosts(now) }
    val byId = remember(thread) { thread.allPosts.associateBy { it.id.value.toString() } }
    val attachments = remember(thread) { thread.files }
    val boardLabel = "/${thread.key.board.value}/"
    val fileCells =
        remember(attachments, boardLabel) {
            attachments.map { MediaCell(id = it.id, board = boardLabel) }
        }
    val attachmentsById = remember(attachments) { attachments.associateBy { it.id } }
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
            watching = watching,
            saved = saved,
            onToggleSaved = onToggleSaved,
            layout = layout,
            onLayoutChange = { layout = it },
            files = fileCells,
            onOpenFile = { cell ->
                val index = attachments.indexOfFirst { it.id == cell.id }
                if (index >= 0) onOpenFile(index)
            },
            fileTile = { cell, tileModifier ->
                attachmentsById[cell.id]?.let { attachment ->
                    Box(tileModifier.clip(RoundedCornerShape(NextRadius.tile))) {
                        SharpImage(
                            attachment,
                            attachment.originalFileName,
                            Modifier.fillMaxSize(),
                            fill = Modifier.fillMaxSize(),
                            // Thread jumps must not start full-video thumbnail decoders.
                            videoFrame = false,
                        )
                        if (attachment.isSpoiler) SpoilerCover(Modifier.matchParentSize())
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
                    PostCommentText(
                        comment = entry.comment,
                        selectable = true,
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
                val postAttachments = byId[post.id]?.attachments.orEmpty()
                if (postAttachments.size == 1) {
                    val attachment = postAttachments.first()
                    Box(
                        modifier
                            .aspectRatio(attachment.threadPreviewAspect())
                            .clickable { thread.firstFileIndex(post.id)?.let(onOpenFile) },
                    ) {
                        // Use the site's thumbnail: a long jump can compose several video posts
                        // just before the viewer needs the same limited decoder resources.
                        SharpImage(
                            attachment,
                            attachment.originalFileName,
                            Modifier.fillMaxSize(),
                            fill = Modifier.fillMaxSize(),
                            videoFrame = false,
                            contentScale = ContentScale.Fit,
                        )
                        // Opening it still asks again in the viewer: the cover is lifted per file there.
                        if (attachment.isSpoiler) SpoilerCover(Modifier.matchParentSize())
                    }
                } else if (postAttachments.size > 1) {
                    val pagerState = rememberPagerState(pageCount = { postAttachments.size })
                    val firstIndex = thread.firstFileIndex(post.id)
                    Column(modifier = modifier) {
                        HorizontalPager(
                            state = pagerState,
                            modifier =
                                Modifier.fillMaxWidth().aspectRatio(
                                    postAttachments.first().threadPreviewAspect(),
                                ),
                            beyondViewportPageCount = 0,
                        ) { page ->
                            val attachment = postAttachments[page]
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clickable { firstIndex?.let { onOpenFile(it + page) } },
                            ) {
                                SharpImage(
                                    attachment,
                                    attachment.originalFileName,
                                    Modifier.fillMaxSize(),
                                    fill = Modifier.fillMaxSize(),
                                    videoFrame = false,
                                    contentScale = ContentScale.Fit,
                                )
                                if (attachment.isSpoiler) SpoilerCover(Modifier.matchParentSize())
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${pagerState.currentPage + 1} of ${postAttachments.size}",
                            style = NextType.labelMedium,
                            color = next.muted,
                        )
                    }
                }
            },
        )
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
internal fun MediaDestination(
    browser: Browser,
    downloads: MediaDownloads,
    route: Route.Media,
) {
    val thread by browser.thread.collectAsState()
    val settings by browser.settings.current.collectAsState()
    val open = (thread as? Load.Ready)?.value?.takeIf { it.key == route.thread }
    val files = remember(open) { open?.files.orEmpty() }
    MediaViewer(
        playback = settings.playbackPreferences.resolve(route.thread.provider, route.thread.board),
        files = files,
        startIndex = route.index,
        onPageChanged = { browser.mediaScroll.select(route, it) },
        onClose = { browser.back() },
        onSave = { file -> downloads.save(file, route.thread, open?.subject) },
    )
}
