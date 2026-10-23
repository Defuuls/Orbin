package com.orbin.ios

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.FormFactor
import com.orbin.core.model.ProviderId
import com.orbin.core.model.TABLET_MIN_CATALOG_COLUMNS
import com.orbin.core.model.feedColumns
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_search_follow_boards
import com.orbin.uinext.BoardsScreen
import com.orbin.uinext.FeedScreen
import com.orbin.uinext.NextDestination
import com.orbin.uinext.NextPullToRefresh
import com.orbin.uinext.PlatformSegments
import com.orbin.uinext.SearchScreen
import com.orbin.uinext.SearchState
import com.orbin.uinext.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

// The tabs and the pages over them: feed, boards, downloads, settings and search.

@Composable
internal fun FeedDestination(
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
internal fun BoardsDestination(browser: Browser) {
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
internal fun DownloadsDestination(
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
internal fun SettingsDestination(
    browser: Browser,
    lock: AppLock,
    backup: IosBackup,
    formFactor: FormFactor,
    version: AppVersion,
) {
    val settings by browser.settings.current.collectAsState()
    val backupState by backup.state.collectAsState()
    val imageLoader = SingletonImageLoader.get(LocalPlatformContext.current)
    val uriHandler = LocalUriHandler.current
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
            ) {
                settingsGroups(settings, clearArmed, imageCacheCleared, backupState, formFactor, version)
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
                SettingIds.PRIVACY -> uriHandler.openUri(PRIVACY_POLICY_URL)
                SettingIds.CONTACT -> uriHandler.openUri(CONTACT_URL)
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
    )
}

@Composable
internal fun SearchDestination(browser: Browser) {
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
