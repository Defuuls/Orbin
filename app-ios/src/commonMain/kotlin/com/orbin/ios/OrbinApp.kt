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
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_search_follow_boards
import com.orbin.uinext.BoardScreen
import com.orbin.uinext.BoardsScreen
import com.orbin.uinext.FeedScreen
import com.orbin.uinext.NextError
import com.orbin.uinext.NextLoading
import com.orbin.uinext.NextPlatform
import com.orbin.uinext.NextTheme
import com.orbin.uinext.SearchScreen
import com.orbin.uinext.SearchState
import com.orbin.uinext.ThreadScreen
import org.jetbrains.compose.resources.stringResource
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
            Route.Feed -> FeedDestination(browser)
            Route.Boards -> BoardsDestination(browser)
            Route.Search -> SearchDestination(browser)
            is Route.Catalog -> CatalogDestination(browser, route.board)
            is Route.ThreadPage -> ThreadDestination(browser)
            is Route.Media -> MediaDestination(browser, route)
        }
    }
}

@Composable
private fun FeedDestination(browser: Browser) {
    val feed by browser.feed.collectAsState()
    val visited by remember { browser.visitedKeys() }.collectAsState(emptySet())
    Loaded(feed, onRetry = browser::retry) { threads ->
        val now = remember(threads) { Clock.System.now().toEpochMilliseconds() }
        val rows = remember(threads, visited) { threads.map { it.toFeedRow(now, read = it.thread.key in visited) } }
        val byRow = remember(threads) { threads.associateBy { it.feedRowId } }
        FeedScreen(
            rows = rows,
            onOpenRow = { row -> byRow[row.id]?.let { browser.openThread(it.thread.key) } },
            thumbnail = { row, modifier -> byRow[row.id]?.let { CatalogThumbnail(it.thread, modifier) } },
            onOpenBoards = { browser.openTab(Route.Boards) },
        )
    }
}

@Composable
private fun BoardsDestination(browser: Browser) {
    val boards by browser.boards.collectAsState()
    val followed by browser.followed.collectAsState()
    Loaded(boards, onRetry = browser::retry) { list ->
        val byTile = remember(list) { list.associateBy { it.tileId } }
        BoardsScreen(
            boards =
                remember(list, followed) {
                    list.map { it.toTile(followed = FollowedBoard(it.provider, it.board.id) in followed) }
                },
            onOpenBoard = { tile -> byTile[tile.id]?.let(browser::openBoard) },
            onFollowBoard = { tile, follow -> byTile[tile.id]?.let { browser.setFollowed(it, follow) } },
            onOpenFeed = { browser.openTab(Route.Feed) },
            onOpenSearch = browser::openSearch,
        )
    }
}

@Composable
private fun SearchDestination(browser: Browser) {
    val query by browser.search.query.collectAsState()
    val results by browser.search.results.collectAsState()
    val followed by browser.followed.collectAsState()
    val followBoards = stringResource(Res.string.ios_search_follow_boards)
    val threads = (results as? Load.Ready)?.value.orEmpty()
    val byRow = remember(threads) { threads.associateBy { it.feedRowId } }
    val state =
        remember(results, followed, followBoards) {
            when (val load = results) {
                null -> SearchState.Idle
                Load.Loading -> SearchState.Loading
                is Load.Failed -> SearchState.Error(load.message)
                // Nothing followed is nothing to search, which Android says rather than "No matches".
                is Load.Ready ->
                    if (followed.isEmpty()) {
                        SearchState.Error(
                            followBoards,
                        )
                    } else {
                        SearchState.Results(threads.map { it.toSearchRow() })
                    }
            }
        }
    SearchScreen(
        query = query,
        onQueryChange = browser.search::setQuery,
        onSearch = { browser.search.run() },
        state = state,
        onOpenRow = { row -> byRow[row.id]?.let { browser.openThread(it.thread.key) } },
    )
}

@Composable
private fun CatalogDestination(
    browser: Browser,
    board: SiteBoard,
) {
    val catalog by browser.catalog.collectAsState()
    val visited by remember(board) { browser.visitedThreads(board) }.collectAsState(emptySet())
    val unread by remember(board) { browser.watched.unread(board) }.collectAsState(emptyMap())
    Loaded(catalog, onRetry = browser::retry) { threads ->
        val now = remember(threads) { Clock.System.now().toEpochMilliseconds() }
        val rows =
            remember(threads, visited, unread) {
                threads.map { thread ->
                    val number = thread.key.thread.value
                    thread.toRow(now, read = number in visited, unread = unread[number] ?: 0)
                }
            }
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
    val firstUnread by browser.firstUnreadPostId.collectAsState()
    Loaded(thread, onRetry = browser::retry) { loaded ->
        val watching by remember(loaded.key) { browser.watched.watching(loaded.key) }.collectAsState(false)
        ThreadContent(
            thread = loaded,
            watching = watching,
            firstUnreadPostId = firstUnread,
            onWatch = { browser.watched.toggle(loaded) },
            onOpenFile = browser::openMedia,
        )
    }
}

@Composable
private fun ThreadContent(
    thread: Thread,
    watching: Boolean,
    firstUnreadPostId: String?,
    onWatch: () -> Unit,
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
        watching = watching,
        onWatch = onWatch,
        firstUnreadPostId = firstUnreadPostId,
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
    val files =
        remember(thread) {
            (thread as? Load.Ready)
                ?.value
                ?.takeIf { it.key == route.thread }
                ?.files
                .orEmpty()
        }
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
