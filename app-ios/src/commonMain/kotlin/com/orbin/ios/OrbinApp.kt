package com.orbin.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.Thread
import com.orbin.core.ui.post.PostCommentText
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_search_follow_boards
import com.orbin.uinext.BoardScreen
import com.orbin.uinext.BoardsScreen
import com.orbin.uinext.FeedScreen
import com.orbin.uinext.LockScreen
import com.orbin.uinext.NextDestination
import com.orbin.uinext.NextError
import com.orbin.uinext.NextLoading
import com.orbin.uinext.NextPlatform
import com.orbin.uinext.NextTheme
import com.orbin.uinext.SearchScreen
import com.orbin.uinext.SearchState
import com.orbin.uinext.SettingsScreen
import com.orbin.uinext.ThreadScreen
import com.orbin.uinext.next
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * The whole iOS app: the shared `ui-next` screens over [browser]. Back is the system edge swipe,
 * which Compose Multiplatform delivers to [NavigationBackHandler] on iOS.
 */
@Composable
fun OrbinApp(
    browser: Browser,
    lock: AppLock,
    downloads: MediaDownloads,
) {
    val backStack by browser.backStack.collectAsState()
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = backStack.size > 1,
        onBackCompleted = { browser.back() },
    )

    val settings by browser.settings.current.collectAsState()
    NextTheme(
        darkTheme =
            when (settings.themeMode) {
                AppThemeMode.SYSTEM -> null
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            },
        amoled = settings.amoled,
        platform = NextPlatform.IOS,
    ) {
        Box(Modifier.fillMaxSize()) {
            Destination(browser, lock, downloads, backStack.last())
            LockCover(lock)
        }
    }
}

@Composable
private fun Destination(
    browser: Browser,
    lock: AppLock,
    downloads: MediaDownloads,
    route: Route,
) {
    when (route) {
        Route.Feed -> FeedDestination(browser)
        Route.Boards -> BoardsDestination(browser)
        Route.Downloads -> DownloadsDestination(browser, downloads)
        Route.Search -> SearchDestination(browser)
        Route.Settings -> SettingsDestination(browser, lock)
        is Route.Catalog -> CatalogDestination(browser, route.board)
        is Route.ThreadPage -> ThreadDestination(browser)
        is Route.Media -> MediaDestination(browser, downloads, route)
    }
}

/** The app lock over everything: the lock screen while locked, a blank cover while hidden. */
@Composable
private fun LockCover(lock: AppLock) {
    val state by lock.state.collectAsState()
    if (!state.locked && !state.obscured) return
    // Taps stop here rather than reaching the app underneath.
    Box(Modifier.fillMaxSize().background(next.background).pointerInput(Unit) {}) {
        if (state.locked) LockScreen(message = state.message, unlocking = state.unlocking, onUnlock = { lock.unlock() })
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
            onOpenDownloads = { browser.openTab(Route.Downloads) },
            onSettings = { browser.open(Route.Settings) },
        )
    }
}

@Composable
private fun BoardsDestination(browser: Browser) {
    val boards by browser.boards.collectAsState()
    val followed by browser.followed.collectAsState()
    val hideNsfw by remember { browser.settings.current.map { it.hideNsfwBoards } }.collectAsState(false)
    Loaded(boards, onRetry = browser::retry) { all ->
        // Hidden NSFW boards leave the list, as Android's boards list drops them.
        val list = remember(all, hideNsfw) { if (hideNsfw) all.filterNot { it.board.isNsfw } else all }
        val byTile = remember(list) { list.associateBy { it.tileId } }
        BoardsScreen(
            boards =
                remember(list, followed) {
                    list.map { it.toTile(followed = FollowedBoard(it.provider, it.board.id) in followed) }
                },
            onOpenBoard = { tile -> byTile[tile.id]?.let(browser::openBoard) },
            onFollowBoard = { tile, follow -> byTile[tile.id]?.let { browser.setFollowed(it, follow) } },
            onOpenFeed = { browser.openTab(Route.Feed) },
            onOpenDownloads = { browser.openTab(Route.Downloads) },
            onOpenSearch = { browser.open(Route.Search) },
            onOpenSettings = { browser.open(Route.Settings) },
        )
    }
}

@Composable
private fun DownloadsDestination(
    browser: Browser,
    downloads: MediaDownloads,
) {
    val records by downloads.records.collectAsState()
    DownloadsScreen(
        records = records,
        onRetry = downloads::retry,
        onClear = downloads::clear,
        onDestination = { destination ->
            when (destination) {
                NextDestination.FEED -> browser.openTab(Route.Feed)
                NextDestination.BOARDS -> browser.openTab(Route.Boards)
                NextDestination.DOWNLOADS -> Unit
                NextDestination.SETTINGS -> browser.open(Route.Settings)
            }
        },
    )
}

@Composable
private fun SettingsDestination(
    browser: Browser,
    lock: AppLock,
) {
    val settings by browser.settings.current.collectAsState()
    val imageLoader = SingletonImageLoader.get(LocalPlatformContext.current)
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf<String?>(null) }
    var clearArmed by remember { mutableStateOf(false) }
    var imageCacheCleared by remember { mutableStateOf(false) }
    SettingsScreen(
        groups =
            remember(
                settings,
                clearArmed,
                imageCacheCleared,
            ) { settingsGroups(settings, clearArmed, imageCacheCleared) },
        expandedId = expanded,
        showRail = false,
        onActivate = { item ->
            when (item.id) {
                SettingIds.HIDE_NSFW -> browser.settings.setHideNsfwBoards(!settings.hideNsfwBoards)
                SettingIds.COVER_VIOLENT -> browser.settings.setCoverViolentMedia(!settings.coverViolentMedia)
                SettingIds.AMOLED -> browser.settings.setAmoled(!settings.amoled)
                SettingIds.APP_LOCK -> lock.setLockEnabled(!settings.biometricLockEnabled)
                SettingIds.THEME -> expanded = if (expanded == item.id) null else item.id
                SettingIds.CLEAR_ACTIVITY ->
                    if (clearArmed) {
                        clearArmed = false
                        browser.settings.clearActivity()
                    } else {
                        clearArmed = true
                    }
                SettingIds.CLEAR_IMAGE_CACHE ->
                    scope.launch {
                        imageLoader.memoryCache?.clear()
                        withContext(Dispatchers.IO) { imageLoader.diskCache?.clear() }
                        imageCacheCleared = true
                    }
            }
        },
        onSelectOption = { item, index ->
            if (item.id == SettingIds.THEME) AppThemeMode.entries.getOrNull(index)?.let(browser.settings::setThemeMode)
            expanded = null
        },
    )
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
    Box(modifier) {
        AsyncImage(
            model = attachment.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (attachment.isSpoiler) SpoilerCover(Modifier.matchParentSize())
    }
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
                Box(modifier.clickable { thread.firstFileIndex(post.id)?.let(onOpenFile) }) {
                    AsyncImage(
                        model = attachment.thumbnailUrl,
                        contentDescription = attachment.originalFileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Opening it still asks again in the viewer: the cover is lifted per file there.
                    if (attachment.isSpoiler) SpoilerCover(Modifier.matchParentSize())
                }
            }
        },
    )
}

@Composable
private fun MediaDestination(
    browser: Browser,
    downloads: MediaDownloads,
    route: Route.Media,
) {
    val thread by browser.thread.collectAsState()
    val open = (thread as? Load.Ready)?.value?.takeIf { it.key == route.thread }
    val files = remember(open) { open?.files.orEmpty() }
    MediaViewer(
        files = files,
        startIndex = route.index,
        onClose = { browser.back() },
        onSave = { file -> downloads.save(file, route.thread, open?.subject) },
    )
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
