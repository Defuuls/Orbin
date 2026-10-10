package com.orbin.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.FormFactor
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.core.model.ProviderId
import com.orbin.core.model.TABLET_MIN_CATALOG_COLUMNS
import com.orbin.core.model.Thread
import com.orbin.core.model.catalogColumns
import com.orbin.core.model.comparator
import com.orbin.core.model.feedColumns
import com.orbin.core.model.showsTwoPanes
import com.orbin.core.ui.post.PostCommentText
import com.orbin.domain.repository.ThreadSyncRepository
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_media_play_video
import com.orbin.ios.resources.ios_pencil_download_all
import com.orbin.ios.resources.ios_pencil_share
import com.orbin.ios.resources.ios_pencil_unwatch
import com.orbin.ios.resources.ios_pencil_watch
import com.orbin.ios.resources.ios_search_follow_boards
import com.orbin.uinext.BoardScreen
import com.orbin.uinext.BoardsScreen
import com.orbin.uinext.FeedScreen
import com.orbin.uinext.LockScreen
import com.orbin.uinext.MediaCell
import com.orbin.uinext.NextDestination
import com.orbin.uinext.NextError
import com.orbin.uinext.NextLoading
import com.orbin.uinext.NextPlatform
import com.orbin.uinext.NextPullToRefresh
import com.orbin.uinext.NextTheme
import com.orbin.uinext.PlatformSegments
import com.orbin.uinext.SearchScreen
import com.orbin.uinext.SearchState
import com.orbin.uinext.SettingsScreen
import com.orbin.uinext.SyncSettingIds
import com.orbin.uinext.ThreadLayout
import com.orbin.uinext.ThreadScreen
import com.orbin.uinext.next
import com.orbin.uinext.syncSettingRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.flowOf
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
    backup: IosBackup,
    formFactor: FormFactor = FormFactor.PHONE,
    version: AppVersion = AppVersion(),
    sync: ThreadSyncRepository? = null,
) {
    val backStack by browser.backStack.collectAsState()
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = backStack.size > 1,
        onBackCompleted = { browser.back() },
    )

    val settings by browser.settings.current.collectAsState()
    val lockState by lock.state.collectAsState()
    val fontScale = rememberSystemFontScale()
    NextTheme(
        darkTheme =
            when (settings.themeMode) {
                AppThemeMode.SYSTEM -> null
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            },
        amoled = settings.amoled,
        fontScale = fontScale,
        platform = NextPlatform.IOS,
    ) {
        // Each screen's scroll position outlives it being covered or left, as on Android.
        val stateHolder = rememberSaveableStateHolder()
        val routeStates = remember { RouteStates() }
        LaunchedEffect(backStack) { routeStates.visit(backStack).forEach(stateHolder::removeState) }
        Box(Modifier.fillMaxSize()) {
            if (!lockState.locked && !lockState.obscured) {
                val route = backStack.last()
                stateHolder.SaveableStateProvider(route.stateKey) {
                    Destination(browser, lock, downloads, backup, formFactor, version, route, sync)
                }
            }
            LockCover(lock, lockState)
        }
    }
}

@Composable
private fun Destination(
    browser: Browser,
    lock: AppLock,
    downloads: MediaDownloads,
    backup: IosBackup,
    formFactor: FormFactor,
    version: AppVersion,
    route: Route,
    sync: ThreadSyncRepository?,
) {
    when (route) {
        Route.Feed -> FeedDestination(browser, formFactor)
        Route.Boards -> BoardsDestination(browser)
        Route.Downloads -> DownloadsDestination(browser, downloads)
        Route.Search -> SearchDestination(browser)
        Route.Settings -> SettingsDestination(browser, lock, backup, formFactor, version, sync)
        is Route.Catalog -> CatalogDestination(browser, route.board, formFactor)
        is Route.ThreadPage -> {
            val backStack by browser.backStack.collectAsState()
            val previous = backStack.getOrNull(backStack.lastIndex - 1)
            // The catalog stays beside the thread only in a window with room for both: an iPad Pro
            // 11-inch full screen either way up (834 x 1210pt), not a narrow Stage Manager, Split
            // View or Slide Over window, which gets the thread alone as a phone does.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val split = showsTwoPanes(maxWidth.value.toInt(), maxHeight.value.toInt())
                if (formFactor == FormFactor.TABLET && split && previous is Route.Catalog) {
                    ThreadSplitDestination(browser, previous.board, formFactor, downloads)
                } else {
                    ThreadDestination(browser, downloads)
                }
            }
        }
        is Route.Media -> MediaDestination(browser, downloads, route)
    }
}

/** The app lock over everything: the lock screen while locked, a blank cover while hidden. */
@Composable
private fun LockCover(
    lock: AppLock,
    state: LockState,
) {
    if (!state.locked && !state.obscured) return
    // Taps stop here rather than reaching the app underneath.
    Box(Modifier.fillMaxSize().background(next.background).pointerInput(Unit) {}) {
        if (state.locked) LockScreen(message = state.message, unlocking = state.unlocking, onUnlock = { lock.unlock() })
    }
}

@Composable
private fun FeedDestination(
    browser: Browser,
    formFactor: FormFactor,
) {
    val feed by browser.feed.collectAsState()
    val settings by browser.settings.current.collectAsState()
    val site by browser.activeSite.collectAsState()
    val visited by remember { browser.visitedKeys() }.collectAsState(emptySet())
    NextPullToRefresh(
        isRefreshing = feed is Load.Loading,
        onRefresh = browser::retry,
        modifier = Modifier.fillMaxSize(),
    ) {
        Loaded(feed, onRetry = browser::retry) { threads ->
            val now = remember(threads) { Clock.System.now().toEpochMilliseconds() }
            val rows = remember(threads, visited) { threads.map { it.toFeedRow(now, read = it.thread.key in visited) } }
            val byRow = remember(threads) { threads.associateBy { it.feedRowId } }
            // Columns as on Android: one on a phone or a narrow window (split view), the reader's choice
            // on an iPad's full width.
            BoxWithConstraints {
                FeedScreen(
                    rows = rows,
                    columns = feedColumns(formFactor, maxWidth.value.toInt(), maxHeight.value.toInt(), settings),
                    onOpenRow = { row -> byRow[row.id]?.let { browser.openThread(it.thread.key) } },
                    // Nothing plays in the feed, as on Android: a video shows its picture and a
                    // play badge until its thread is opened. Catalogs keep their looping previews.
                    thumbnail = { row, modifier ->
                        byRow[row.id]?.let { CatalogThumbnail(it.thread, modifier, playsPreview = false) }
                    },
                    onOpenBoards = { browser.openTab(Route.Boards) },
                    onOpenDownloads = { browser.openTab(Route.Downloads) },
                    onSettings = { browser.open(Route.Settings) },
                    headerContent = { SiteSwitcher(browser, site) },
                    uniformCards = true,
                )
            }
        }
    }
}

@Composable
private fun BoardsDestination(browser: Browser) {
    val boards by browser.boards.collectAsState()
    val followed by browser.followed.collectAsState()
    val unreachable by browser.unreachableSites.collectAsState()
    val site by browser.activeSite.collectAsState()
    val hideNsfw by remember { browser.settings.current.map { it.hideNsfwBoards } }.collectAsState(false)
    Loaded(boards, onRetry = browser::retry) { all ->
        // One site at a time, as Android's boards list; hidden NSFW boards leave it, as there.
        val list =
            remember(all, site, hideNsfw) {
                all.filter { it.provider == site && !(hideNsfw && it.board.isNsfw) }
            }
        val byTile = remember(list) { list.associateBy { it.tileId } }
        BoardsScreen(
            subtitle = boardsSubtitle(list.size, unreachable),
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
            headerContent = { SiteSwitcher(browser, site, gapAfter = 12.dp) },
        )
    }
}

/** Android's site switcher, on the feed and Boards: a segment per site, shown when there is more than one. */
@Composable
private fun SiteSwitcher(
    browser: Browser,
    active: ProviderId,
    // Boards puts its search field straight under its header; the feed spaces its own.
    gapAfter: Dp = 0.dp,
) {
    if (browser.sites.size < 2) return
    PlatformSegments(
        labels = browser.sites.map { it.name },
        selected = browser.sites.indexOfFirst { it.id == active },
        onSelect = { browser.selectSite(browser.sites[it].id) },
    )
    Spacer(Modifier.height(gapAfter))
}

/**
 * The Boards title's subtitle when a site could not be reached (a network blocking it, say), so it
 * reads as missing rather than never having been there; null keeps the usual board count.
 */
internal fun boardsSubtitle(
    boardCount: Int,
    unreachable: List<String>,
): String? {
    if (unreachable.isEmpty()) return null
    val count = if (boardCount == 1) "1 board" else "$boardCount boards"
    return "$count · Couldn't reach ${unreachable.joinToString()}; tried again when you reopen Boards"
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
    backup: IosBackup,
    formFactor: FormFactor,
    version: AppVersion,
    sync: ThreadSyncRepository?,
) {
    val settings by browser.settings.current.collectAsState()
    val syncStatus by remember(sync) { sync?.state ?: flowOf(null) }.collectAsState(null)
    val backupState by backup.state.collectAsState()
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
                backupState,
                formFactor,
                version,
                syncStatus,
            ) {
                val groups = settingsGroups(settings, clearArmed, imageCacheCleared, backupState, formFactor, version)
                if (sync == null) {
                    groups
                } else {
                    // Thread sync, the same rows as Android, between the preferences and the data rows.
                    val rows =
                        syncSettingRows(
                            folderUrl = syncStatus?.folderUrl.orEmpty(),
                            username = syncStatus?.username.orEmpty(),
                            hasPassword = syncStatus?.hasPassword == true,
                            lastSyncedMillis = syncStatus?.lastSyncedMillis,
                            error = syncStatus?.error,
                            nowMillis = Clock.System.now().toEpochMilliseconds(),
                        )
                    groups.take(1) + ("" to rows) + groups.drop(1)
                }
            },
        expandedId = expanded,
        showRail = false,
        onActivate = { item ->
            when (item.id) {
                SettingIds.HIDE_NSFW -> {
                    Haptics.light()
                    browser.settings.setHideNsfwBoards(!settings.hideNsfwBoards)
                }
                SettingIds.COVER_VIOLENT -> {
                    Haptics.light()
                    browser.settings.setCoverViolentMedia(!settings.coverViolentMedia)
                }
                SettingIds.AMOLED -> {
                    Haptics.light()
                    browser.settings.setAmoled(!settings.amoled)
                }
                SettingIds.APP_LOCK -> {
                    Haptics.light()
                    lock.setLockEnabled(!settings.biometricLockEnabled)
                }
                SettingIds.THEME, SettingIds.FEED_COLUMNS, SettingIds.CATALOG_COLUMNS -> {
                    Haptics.light()
                    expanded = if (expanded == item.id) null else item.id
                }
                SettingIds.CLEAR_ACTIVITY ->
                    if (clearArmed) {
                        clearArmed = false
                        browser.settings.clearActivity {
                            imageLoader.memoryCache?.clear()
                            withContext(Dispatchers.IO) { imageLoader.diskCache?.clear() }
                        }
                        Haptics.success()
                    } else {
                        Haptics.light()
                        clearArmed = true
                    }
                SettingIds.EXPORT_BACKUP -> {
                    Haptics.light()
                    backup.export()
                }
                SettingIds.IMPORT_BACKUP -> {
                    Haptics.light()
                    backup.import()
                }
                SyncSettingIds.FOLDER, SyncSettingIds.USERNAME, SyncSettingIds.PASSWORD -> {
                    Haptics.light()
                    expanded = if (expanded == item.id) null else item.id
                }
                SyncSettingIds.SYNC_NOW ->
                    sync?.let {
                        Haptics.light()
                        scope.launch { it.sync() }
                    }
                SettingIds.CLEAR_IMAGE_CACHE ->
                    scope.launch {
                        imageLoader.memoryCache?.clear()
                        withContext(Dispatchers.IO) { imageLoader.diskCache?.clear() }
                        imageCacheCleared = true
                        Haptics.success()
                    }
            }
        },
        onSelectOption = { item, index ->
            Haptics.light()
            when (item.id) {
                SettingIds.THEME -> AppThemeMode.entries.getOrNull(index)?.let(browser.settings::setThemeMode)
                SettingIds.FEED_COLUMNS -> browser.settings.setFeedColumns(formFactor, index + 1)
                SettingIds.CATALOG_COLUMNS ->
                    browser.settings.setTabletCatalogColumns(TABLET_MIN_CATALOG_COLUMNS + index)
            }
            expanded = null
        },
        onCommitText = { item, text ->
            Haptics.light()
            expanded = null
            val engine = sync ?: return@SettingsScreen
            scope.launch {
                when (item.id) {
                    SyncSettingIds.FOLDER -> engine.setAccount(folderUrl = text)
                    SyncSettingIds.USERNAME -> engine.setAccount(username = text)
                    SyncSettingIds.PASSWORD -> engine.setAccount(password = text)
                }
                engine.sync()
            }
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
    formFactor: FormFactor,
) {
    val catalog by browser.catalog.collectAsState()
    val settings by browser.settings.current.collectAsState()
    val sort by browser.catalogSort.collectAsState()
    val visited by remember(board) { browser.visitedThreads(board) }.collectAsState(emptySet())
    val unread by remember(board) { browser.watched.unread(board) }.collectAsState(emptyMap())
    NextPullToRefresh(
        isRefreshing = catalog is Load.Loading,
        onRefresh = browser::retry,
        modifier = Modifier.fillMaxSize(),
    ) {
        Loaded(catalog, onRetry = browser::retry) { threads ->
            val now = remember(threads) { Clock.System.now().toEpochMilliseconds() }
            val sorted = remember(threads, sort) { threads.sortedWith(sort.comparator()) }
            val rows =
                remember(sorted, visited, unread) {
                    sorted.map { thread ->
                        val number = thread.key.thread.value
                        thread.toRow(now, read = number in visited, unread = unread[number] ?: 0)
                    }
                }
            val byRow = remember(threads) { threads.associateBy { "${it.key.board.value}/${it.key.thread.value}" } }
            // An iPad's chosen column count on its full width; elsewhere the catalog fits its tiles.
            BoxWithConstraints {
                BoardScreen(
                    columns = catalogColumns(formFactor, maxWidth.value.toInt(), maxHeight.value.toInt(), settings),
                    board = "/${board.board.id.value}/",
                    description = board.board.title,
                    itemCount = rows.size,
                    rowAt = { index -> rows.getOrNull(index) },
                    sortLabel = sort.label,
                    onSort = browser::cycleCatalogSort,
                    showRail = false,
                    onOpenRow = { row -> byRow[row.id]?.let { browser.openThread(it.key) } },
                    thumbnail = { row, modifier -> byRow[row.id]?.let { CatalogThumbnail(it, modifier) } },
                )
            }
        }
    }
}

/**
 * On iPad, keep the board catalog beside its selected thread. The browser stack still owns
 * navigation, so the system back gesture returns to the catalog as it does on Android.
 */
@Composable
private fun ThreadSplitDestination(
    browser: Browser,
    board: SiteBoard,
    formFactor: FormFactor,
    downloads: MediaDownloads,
) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxSize()) {
            CatalogDestination(browser, board, formFactor)
        }
        Box(Modifier.width(0.5.dp).fillMaxSize().background(next.hairline))
        Box(Modifier.weight(1.4f).fillMaxSize()) {
            ThreadDestination(browser, downloads)
        }
    }
}

@Composable
private fun CatalogThumbnail(
    thread: CatalogThread,
    modifier: Modifier,
    // Whether a video or GIF loops in place, muted; otherwise it shows its picture and a play badge.
    playsPreview: Boolean = true,
) {
    val attachment = thread.originalPost.attachments.firstOrNull() ?: return
    Box(modifier) {
        val loops = playsPreview && attachment.isPlayable && (!attachment.isWebM || supportsWebM)
        SharpImage(
            attachment,
            contentDescription = null,
            Modifier.fillMaxSize(),
            fill = Modifier.fillMaxSize(),
            // A looping preview covers the picture anyway, so it needs no still frame under it.
            videoFrame = !loops,
        )
        if (loops) NativeInlineLoop(attachment.sourceUrl, Modifier.matchParentSize())
        if (attachment.isSpoiler) {
            SpoilerCover(Modifier.matchParentSize())
        } else if (!loops && attachment.isPlayable) {
            PlayBadge(Modifier.align(Alignment.Center))
        }
    }
}

/** A video whose frames this device can decode: WebM needs iOS 17.4, MP4 and MOV play anywhere. */
private val MediaAttachment.hasDecodableVideo: Boolean
    get() = type == MediaType.VIDEO && (!isWebM || supportsWebM)

/** The mark on a video that is not playing: white on a dark disc, readable over any picture. */
@Composable
private fun PlayBadge(modifier: Modifier) {
    Icon(
        imageVector = Icons.Filled.PlayArrow,
        contentDescription = stringResource(Res.string.ios_media_play_video),
        tint = Color.White,
        modifier =
            modifier
                .size(PLAY_BADGE_SIZE)
                .background(Color.Black.copy(alpha = PLAY_BADGE_SCRIM), CircleShape)
                .padding(PLAY_BADGE_PADDING),
    )
}

private val PLAY_BADGE_SIZE = 40.dp
private val PLAY_BADGE_PADDING = 6.dp
private const val PLAY_BADGE_SCRIM = 0.5f

/**
 * A file's picture at the size it is shown: the site's thumbnail first, so the space fills at
 * once, then, for a still image, the full picture over it. A card or a post's image is far wider
 * than a thumbnail (about 250 pixels), which stretched looks blurry; Android loads full resolution
 * there too. The thumbnail sets the size, so the full picture only ever sharpens it.
 */
@Composable
private fun SharpImage(
    attachment: MediaAttachment,
    contentDescription: String?,
    modifier: Modifier,
    // How the thumbnail takes the space: a card's whole fixed box, or a post's width at the
    // picture's own proportions.
    fill: Modifier = Modifier.fillMaxWidth(),
    videoFrame: Boolean = true,
) {
    // Hovering the Apple Pencil over a picture shows it larger, without opening it.
    PencilHoverPreview(attachment, modifier) {
        AsyncImage(
            model = attachment.thumbnailUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = fill,
        )
        val sharp = attachment.cardImageUrl()
        if (sharp != attachment.thumbnailUrl) {
            AsyncImage(
                model = sharp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        // A video has no full-size picture of its own: show its first frame, decoded from the file.
        if (videoFrame && attachment.hasDecodableVideo) {
            NativeVideoFrame(attachment.sourceUrl, Modifier.matchParentSize())
        }
    }
}

@Composable
private fun ThreadDestination(
    browser: Browser,
    downloads: MediaDownloads,
) {
    val thread by browser.thread.collectAsState()
    val firstUnread by browser.firstUnreadPostId.collectAsState()
    NextPullToRefresh(
        isRefreshing = thread is Load.Loading,
        onRefresh = browser::retry,
        modifier = Modifier.fillMaxSize(),
    ) {
        Loaded(thread, onRetry = browser::retry) { loaded ->
            val watching by remember(loaded.key) { browser.watched.watching(loaded.key) }.collectAsState(false)
            ThreadContent(
                thread = loaded,
                watching = watching,
                firstUnreadPostId = firstUnread,
                onWatch = { browser.watched.toggle(loaded) },
                onOpenFile = browser::openMedia,
                onDownloadAll = {
                    loaded.files.forEach { file -> downloads.save(file, loaded.key, loaded.subject) }
                },
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
    onDownloadAll: () -> Unit = {},
    onShare: () -> Unit = {},
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
            posts = posts,
            watching = watching,
            layout = layout,
            onLayoutChange = { layout = it },
            files = fileCells,
            onOpenFile = { cell ->
                val index = attachments.indexOfFirst { it.id == cell.id }
                if (index >= 0) onOpenFile(index)
            },
            fileTile = { cell, tileModifier ->
                attachmentsById[cell.id]?.let { attachment ->
                    Box(tileModifier.clip(RoundedCornerShape(10.dp))) {
                        SharpImage(
                            attachment,
                            attachment.originalFileName,
                            Modifier.fillMaxSize(),
                            fill = Modifier.fillMaxSize(),
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
            onShare = onShare,
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
                        SharpImage(attachment, attachment.originalFileName, Modifier.fillMaxWidth())
                        // Opening it still asks again in the viewer: the cover is lifted per file there.
                        if (attachment.isSpoiler) SpoilerCover(Modifier.matchParentSize())
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
