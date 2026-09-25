package com.orbin.ios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.compose.AsyncImage
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.Thread
import com.orbin.core.ui.post.PostCommentText
import com.orbin.uinext.BoardScreen
import com.orbin.uinext.BoardsScreen
import com.orbin.uinext.NextError
import com.orbin.uinext.NextLoading
import com.orbin.uinext.NextPlatform
import com.orbin.uinext.NextTheme
import com.orbin.uinext.ThreadScreen
import kotlin.time.Clock

/**
 * The whole iOS app: the shared `ui-next` screens over [browser]. Back is the system edge swipe,
 * which Compose Multiplatform delivers to [NavigationBackHandler] on iOS.
 */
@Composable
fun OrbinApp(browser: Browser) {
    val backStack by browser.backStack.collectAsState()
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = backStack.size > 1,
        onBackCompleted = { browser.back() },
    )

    NextTheme(platform = NextPlatform.IOS) {
        when (val route = backStack.last()) {
            Route.Boards -> BoardsDestination(browser)
            is Route.Catalog -> CatalogDestination(browser, route.board)
            is Route.ThreadPage -> ThreadDestination(browser)
            is Route.Media -> MediaDestination(browser, route)
        }
    }
}

@Composable
private fun BoardsDestination(browser: Browser) {
    val boards by browser.boards.collectAsState()
    Loaded(boards, onRetry = browser::retry) { list ->
        val byTile = remember(list) { list.associateBy { it.tileId } }
        BoardsScreen(
            boards = remember(list) { list.map { it.toTile() } },
            onOpenBoard = { tile -> byTile[tile.id]?.let(browser::openBoard) },
        )
    }
}

@Composable
private fun CatalogDestination(
    browser: Browser,
    board: SiteBoard,
) {
    val catalog by browser.catalog.collectAsState()
    Loaded(catalog, onRetry = browser::retry) { threads ->
        val now = remember(threads) { Clock.System.now().toEpochMilliseconds() }
        val rows = remember(threads) { threads.map { it.toRow(now) } }
        val byRow = remember(threads) { threads.associateBy { "${it.key.board.value}/${it.key.thread.value}" } }
        BoardScreen(
            board = "/${board.board.id.value}/",
            description = board.board.title,
            itemCount = rows.size,
            rowAt = { index -> rows.getOrNull(index) },
            showRail = false,
            onOpenRow = { row -> byRow[row.id]?.let { browser.openThread(it.key) } },
            thumbnail = { row, modifier -> byRow[row.id]?.let { CatalogThumbnail(it, modifier) } },
        )
    }
}

@Composable
private fun CatalogThumbnail(
    thread: CatalogThread,
    modifier: Modifier,
) {
    val attachment = thread.originalPost.attachments.firstOrNull() ?: return
    AsyncImage(
        model = attachment.thumbnailUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Composable
private fun ThreadDestination(browser: Browser) {
    val thread by browser.thread.collectAsState()
    Loaded(thread, onRetry = browser::retry) { loaded -> ThreadContent(loaded, onOpenFile = browser::openMedia) }
}

@Composable
private fun ThreadContent(
    thread: Thread,
    onOpenFile: (index: Int) -> Unit,
) {
    val now = remember(thread) { Clock.System.now().toEpochMilliseconds() }
    val posts = remember(thread) { thread.toPosts(now) }
    val byId = remember(thread) { thread.allPosts.associateBy { it.id.value.toString() } }
    val uriHandler = LocalUriHandler.current
    // Where a tapped quote asks the list to scroll; cleared once the screen has scrolled there.
    var scrollTarget by remember(thread) { mutableStateOf<String?>(null) }
    ThreadScreen(
        subject = thread.subject?.takeIf { it.isNotBlank() } ?: "No.${thread.key.thread.value}",
        board = "/${thread.key.board.value}/",
        posts = posts,
        scrollToPostId = scrollTarget,
        onScrollConsumed = { scrollTarget = null },
        body = { post ->
            byId[post.id]?.let { entry ->
                PostCommentText(
                    comment = entry.comment,
                    selectable = true,
                    onQuoteClick = { target -> scrollTarget = target.value.toString() },
                    onLinkClick = { url -> safeExternalLink(url)?.let(uriHandler::openUri) },
                )
            }
        },
        media = { post, modifier ->
            byId[post.id]?.attachments?.firstOrNull()?.let { attachment ->
                AsyncImage(
                    model = attachment.thumbnailUrl,
                    contentDescription = attachment.originalFileName,
                    contentScale = ContentScale.Crop,
                    modifier = modifier.clickable { thread.firstFileIndex(post.id)?.let(onOpenFile) },
                )
            }
        },
    )
}

@Composable
private fun MediaDestination(
    browser: Browser,
    route: Route.Media,
) {
    val thread by browser.thread.collectAsState()
    val files = remember(thread) { (thread as? Load.Ready)?.value?.takeIf { it.key == route.thread }?.files.orEmpty() }
    MediaViewer(files = files, startIndex = route.index, onClose = { browser.back() })
}

@Composable
private fun <T> Loaded(
    load: Load<T>,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    when (load) {
        Load.Loading -> NextLoading(Modifier.fillMaxSize())
        is Load.Failed -> NextError(load.message, onRetry = onRetry)
        is Load.Ready -> content(load.value)
    }
}
