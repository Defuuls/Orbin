package com.orbin.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.orbin.core.model.AppColorTheme
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.Board
import com.orbin.core.model.DohProvider
import com.orbin.core.model.FormFactor
import com.orbin.core.model.HomeFeedMode
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.ProviderId
import com.orbin.core.model.TABLET_MIN_CATALOG_COLUMNS
import com.orbin.core.model.feedColumns
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_boards_no_visible
import com.orbin.ios.resources.ios_boards_source_unavailable
import com.orbin.ios.resources.ios_collection_all_sites
import com.orbin.ios.resources.ios_collection_history
import com.orbin.ios.resources.ios_collection_saved
import com.orbin.ios.resources.ios_collection_watched
import com.orbin.ios.resources.ios_primary_boards
import com.orbin.ios.resources.ios_primary_loading
import com.orbin.ios.resources.ios_primary_retry
import com.orbin.ios.resources.ios_search_follow_boards
import com.orbin.uinext.BoardsScreen
import com.orbin.uinext.FeedScreen
import com.orbin.uinext.MessageScreen
import com.orbin.uinext.NextDestination
import com.orbin.uinext.NextPullToRefresh
import com.orbin.uinext.NextScaffold
import com.orbin.uinext.PlatformSegments
import com.orbin.uinext.SearchScreen
import com.orbin.uinext.SearchState
import com.orbin.uinext.SettingsScreen
import com.orbin.uinext.ThreadCollectionRow
import com.orbin.uinext.ThreadCollectionScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

private const val CLEAR_ARMED_MS = 4_000L

// Home, Boards, Watched, Search and Saved tabs, plus their detail pages.

@Composable
internal fun FeedDestination(
    browser: Browser,
    formFactor: FormFactor,
    playbackAllowed: Boolean = true,
) {
    val feed by browser.feed.collectAsState()
    val boards by browser.boards.collectAsState()
    val settings by browser.settings.current.collectAsState()
    val site by browser.feedSite.collectAsState()
    val mode by browser.feedMode.collectAsState()
    val loadingMore by browser.feedLoadingMore.collectAsState()
    val canLoadMore by browser.feedCanLoadMore.collectAsState()
    val discoveryProgress by browser.feedDiscoveryProgress.collectAsState()
    val contentPolicy by browser.feedContentPolicy.collectAsState()
    val saved by browser.savedThreads.collectAsState(emptyList())
    val visited by remember { browser.visitedKeys() }.collectAsState(emptySet())
    val policyMatches = contentPolicy == HomeFeedContentPolicy(settings.hideNsfwBoards, settings.coverViolentMedia)
    val threads =
        remember(feed, policyMatches) {
            if (policyMatches) {
                (feed as? Load.Ready)?.value.orEmpty().mapNotNull(
                    FeedThread::videoFeedPreview,
                )
            } else {
                emptyList()
            }
        }
    // Empty video batches advance only after their catalogs finish, never while mapping old rows.
    LaunchedEffect(
        threads.isEmpty(),
        loadingMore,
        canLoadMore,
        mode,
        discoveryProgress,
        feed is Load.Ready,
        policyMatches,
    ) {
        val successfulEmptyPage = feed is Load.Ready && policyMatches && threads.isEmpty()
        val discoveryCanAdvance = mode == HomeFeedMode.FOR_YOU && !loadingMore && canLoadMore
        if (successfulEmptyPage && discoveryCanAdvance) {
            browser.loadMoreFeed()
        }
    }
    val now = remember(threads) { Clock.System.now().toEpochMilliseconds() }
    val byKey = remember(saved) { saved.associateBy { it.key } }
    val rows =
        remember(threads, visited, byKey) {
            threads.map {
                it.toFeedRow(now, read = it.thread.key in visited).copy(
                    watched = byKey[it.thread.key]?.isWatched == true,
                    bookmarked = it.thread.key in byKey,
                )
            }
        }
    val byRow = remember(threads) { threads.associateBy { it.feedRowId } }
    var activePreviewId by remember { mutableStateOf<String?>(null) }
    var upcomingPreviewId by remember { mutableStateOf<String?>(null) }
    val preloader = remember { createNativeMediaPreloader() }
    DisposableEffect(preloader) { onDispose { preloader.close() } }
    val foreground =
        FeedPreloadLifecycle(
            preloader,
            if (playbackAllowed) {
                feedPreloadTarget(
                    byRow[activePreviewId],
                    byRow[upcomingPreviewId],
                    settings.playbackPreferences,
                    supportsWebM,
                )
            } else {
                null
            },
        )
    val uriHandler = LocalUriHandler.current
    NextPullToRefresh(
        isRefreshing = feed is Load.Loading,
        onRefresh = browser::retry,
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints {
            NativeMediaPreloadHost(preloader)
            FeedScreen(
                rows = rows,
                following = mode == HomeFeedMode.FOLLOWING,
                onFollowingChange = {
                    browser.selectFeedMode(
                        if (it) HomeFeedMode.FOLLOWING else HomeFeedMode.FOR_YOU,
                    )
                },
                isLoading = !policyMatches || feed is Load.Loading || loadingMore && rows.isEmpty(),
                errorMessage = (feed as? Load.Failed)?.message,
                onRetry = browser::retry,
                onLoadMore =
                    (browser::loadMoreFeed).takeIf {
                        canLoadMore &&
                            !loadingMore &&
                            feed is Load.Ready &&
                            policyMatches
                    },
                loadMoreKey = discoveryProgress,
                hideRailOnScroll = false,
                columns = feedColumns(formFactor, maxWidth.value.toInt(), maxHeight.value.toInt(), settings),
                onOpenRow = { row -> byRow[row.id]?.let { browser.openThread(it.thread.key) } },
                onOpenReplies = { row ->
                    byRow[row.id]?.let {
                        if (settings.openThreadComments) {
                            browser.openThread(
                                it.thread.key,
                            )
                        } else {
                            browser.openThreadVideos(it.thread.key)
                        }
                    }
                },
                repliesOpenComments = settings.openThreadComments,
                onToggleWatch = { row -> byRow[row.id]?.let(browser::toggleFeedWatch) },
                onToggleSaved = { row -> byRow[row.id]?.let(browser::toggleFeedSaved) },
                onShareRow = { row ->
                    byRow[row.id]?.let { shareContent(row.subject, browser.threadWebUrl(it.thread.key)) }
                },
                onActivePreviewChanged = { activePreviewId = it },
                onUpcomingPreviewChanged = { upcomingPreviewId = it },
                mediaContent = { row, modifier, active ->
                    byRow[row.id]?.let { entry ->
                        FeedMedia(
                            feed = entry,
                            playback = settings.playbackPreferences.resolve(entry.provider, entry.thread.key.board),
                            active = active && foreground && playbackAllowed,
                            modifier = modifier,
                            preloader = preloader,
                            onLike = { browser.watchFeed(entry) },
                            onOpenExternal = uriHandler::openUri,
                        )
                    }
                },
                onOpenBoard = { row ->
                    byRow[row.id]?.let { entry ->
                        val key = entry.thread.key
                        val known =
                            (boards as? Load.Ready<List<SiteBoard>>)?.value?.firstOrNull {
                                it.provider == key.provider && it.board.id == key.board
                            }
                        browser.openBoard(
                            known
                                ?: SiteBoard(
                                    key.provider,
                                    browser.siteName(key.provider),
                                    Board(key.board, row.boardTitle),
                                ),
                        )
                    }
                },
                onOpenBoards = { browser.openTab(Route.Boards) },
                onOpenDownloads = { browser.open(Route.Downloads) },
                onOpenSearch = { browser.openTab(Route.Search) },
                onOpenSaved = { browser.openTab(Route.Saved) },
                onOpenWatched = { browser.openTab(Route.Watched) },
                onSettings = { browser.open(Route.Settings) },
                sourceLabels =
                    listOf(stringResource(Res.string.ios_collection_all_sites)) + browser.sites.map { it.name },
                selectedSource = if (site == null) 0 else browser.sites.indexOfFirst { it.id == site } + 1,
                onSelectSource = { index -> browser.selectFeedSite(browser.sites.getOrNull(index - 1)?.id) },
                uniformCards = true,
            )
        }
    }
}

@Composable
internal fun BoardsDestination(browser: Browser) {
    val boards by browser.boards.collectAsState()
    val favorites by browser.favorites.collectAsState()
    val unreachable by browser.unreachableSites.collectAsState()
    val site by browser.activeSite.collectAsState()
    val hideNsfw by remember { browser.settings.current.map { it.hideNsfwBoards } }.collectAsState(false)
    PrimaryLoaded(browser, boards, stringResource(Res.string.ios_primary_boards), NextDestination.BOARDS) { all ->
        // One site at a time, as Android's boards list; hidden NSFW boards leave it, as there.
        val list =
            remember(all, site, hideNsfw) {
                all.filter { it.provider == site && !(hideNsfw && it.board.isNsfw) }
            }
        val byTile = remember(list) { list.associateBy { it.tileId } }
        val sourceUnavailable = all.none { it.provider == site }
        BoardsScreen(
            hideRailOnScroll = false,
            subtitle = boardsSubtitle(list.size, unreachable),
            emptyMessage =
                if (sourceUnavailable) {
                    stringResource(Res.string.ios_boards_source_unavailable, browser.siteName(site))
                } else {
                    stringResource(Res.string.ios_boards_no_visible)
                },
            onRetry = if (sourceUnavailable) browser::retry else null,
            boards =
                remember(list, favorites) {
                    list.map { it.toTile(followed = FollowedBoard(it.provider, it.board.id) in favorites) }
                },
            onOpenBoard = { tile -> byTile[tile.id]?.let(browser::openBoard) },
            onFollowBoard = { tile, follow -> byTile[tile.id]?.let { browser.setFavorite(it, follow) } },
            onOpenFeed = { browser.openTab(Route.Feed) },
            onOpenDownloads = { browser.open(Route.Downloads) },
            onOpenSaved = { browser.openTab(Route.Saved) },
            onOpenWatched = { browser.openTab(Route.Watched) },
            onOpenSearch = { browser.openTab(Route.Search) },
            onOpenSettings = { browser.open(Route.Settings) },
            headerContent = { SiteSwitcher(browser, site, gapAfter = 12.dp) },
        )
    }
}

/** Board directory source selector. Home adds an All sites choice separately. */
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
        onBack = { if (!browser.back()) browser.openTab(Route.Feed) },
        onDestination = { destination ->
            when (destination) {
                NextDestination.FEED -> browser.openTab(Route.Feed)
                NextDestination.BOARDS -> browser.openTab(Route.Boards)
                NextDestination.DOWNLOADS -> Unit
                NextDestination.SEARCH -> browser.openTab(Route.Search)
                NextDestination.SAVED -> browser.openTab(Route.Saved)
                NextDestination.WATCHED -> browser.openTab(Route.Watched)
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
    val boards by browser.boards.collectAsState()
    var playbackScope by remember { mutableStateOf(PlaybackScope()) }
    val playbackChoices =
        remember(boards, settings.playbackPreferences.overrides) {
            buildList {
                add(PlaybackScope() to "App defaults")
                browser.sites.forEach { add(PlaybackScope(it.id.value) to it.name) }
                (boards as? Load.Ready<List<SiteBoard>>)?.value.orEmpty().forEach {
                    add(
                        PlaybackScope(
                            it.provider.value,
                            it.board.id.value,
                        ) to "${it.siteName} · /${it.board.id.value}/",
                    )
                }
                settings.playbackPreferences.overrides.forEach { entry ->
                    if (none { it.first == entry.scope }) {
                        val site = browser.siteName(ProviderId(entry.scope.providerId))
                        add(entry.scope to if (entry.scope.isBoard) "$site · /${entry.scope.boardId}/" else site)
                    }
                }
            }
        }
    val backupState by backup.state.collectAsState()
    val imageLoader = SingletonImageLoader.get(LocalPlatformContext.current)
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf<String?>(null) }
    var clearArmed by remember { mutableStateOf(false) }
    LaunchedEffect(clearArmed) {
        if (clearArmed) {
            delay(CLEAR_ARMED_MS)
            clearArmed = false
        }
    }
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
                playbackScope,
                playbackChoices,
            ) {
                settingsGroups(settings, clearArmed, imageCacheCleared, backupState, formFactor, version) +
                    ("Playback" to playbackRows(settings, playbackChoices, playbackScope))
            },
        expandedId = expanded,
        enabledSiteCount = browser.sites.size,
        showRail = false,
        onBack = { browser.back() },
        onNavigate = { destination ->
            when (destination) {
                "boards", "favorites" -> browser.openTab(Route.Boards)
                "history" -> browser.open(Route.History)
                "watched" -> browser.openTab(Route.Watched)
                "downloads" -> browser.open(Route.Downloads)
                "help" -> uriHandler.openUri("https://github.com/Defuuls/Orbin/wiki")
            }
        },
        onActivate = { item ->
            if (!browser.settings.activatePlayback(item.id, settings, playbackScope)) {
                when (item.id) {
                    SettingIds.HIDE_NSFW -> {
                        Haptics.light()
                        browser.settings.setHideNsfwBoards(!settings.hideNsfwBoards)
                    }
                    SettingIds.COVER_VIOLENT -> {
                        Haptics.light()
                        browser.settings.setCoverViolentMedia(!settings.coverViolentMedia)
                    }
                    SettingIds.OPEN_THREAD_COMMENTS -> {
                        Haptics.light()
                        browser.settings.setOpenThreadComments(!settings.openThreadComments)
                    }
                    SettingIds.AMOLED -> {
                        Haptics.light()
                        browser.settings.setAmoled(!settings.amoled)
                    }
                    SettingIds.APP_LOCK -> {
                        Haptics.light()
                        lock.setLockEnabled(!settings.biometricLockEnabled)
                    }
                    SettingIds.SCREEN_PRIVACY -> {
                        Haptics.light()
                        browser.settings.setScreenPrivacy(!settings.screenPrivacyEnabled)
                    }
                    SettingIds.STRICT_DOH -> {
                        Haptics.light()
                        browser.settings.setStrictDns(!settings.strictDohEnabled)
                    }
                    SettingIds.SAVE_SEARCH_HISTORY -> {
                        Haptics.light()
                        browser.settings.setSaveSearchHistory(!settings.saveSearchHistory)
                    }
                    SettingIds.SAVE_BROWSING_HISTORY -> {
                        Haptics.light()
                        browser.settings.setSaveBrowsingHistory(!settings.saveBrowsingHistory)
                    }
                    SettingIds.DOH_PROVIDER,
                    SettingIds.COLOR_THEME,
                    SettingIds.THEME,
                    SettingIds.FEED_COLUMNS,
                    SettingIds.CATALOG_COLUMNS,
                    "playbackScope",
                    "autoplay",
                    "preloadNextClip",
                    -> {
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
            }
        },
        onSelectOption = { item, index ->
            Haptics.light()
            if (!browser.settings.selectPlaybackChoice(item.id, index, settings, playbackScope)) {
                when (item.id) {
                    "playbackScope" -> playbackChoices.getOrNull(index)?.let { playbackScope = it.first }
                    SettingIds.DOH_PROVIDER ->
                        DohProvider.entries
                            .getOrNull(
                                index,
                            )?.let(browser.settings::setDohProvider)
                    SettingIds.COLOR_THEME ->
                        AppColorTheme.entries
                            .getOrNull(
                                index,
                            )?.let(browser.settings::setColorTheme)
                    SettingIds.THEME -> AppThemeMode.entries.getOrNull(index)?.let(browser.settings::setThemeMode)
                    SettingIds.FEED_COLUMNS -> browser.settings.setFeedColumns(formFactor, index + 1)
                    SettingIds.CATALOG_COLUMNS ->
                        browser.settings.setTabletCatalogColumns(TABLET_MIN_CATALOG_COLUMNS + index)
                }
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
    NextScaffold(
        where = "Search",
        destination = NextDestination.SEARCH,
        onDestination = browser::openDestination,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            SearchScreen(
                query = query,
                onQueryChange = browser.search::setQuery,
                onSearch = { browser.search.run() },
                onBack = { browser.openTab(Route.Feed) },
                state = state,
                onOpenRow = { row -> byRow[row.id]?.let { browser.openThread(it.thread.key) } },
            )
        }
    }
}

internal fun Browser.openDestination(destination: NextDestination) {
    when (destination) {
        NextDestination.FEED -> openTab(Route.Feed)
        NextDestination.SEARCH -> openTab(Route.Search)
        NextDestination.BOARDS -> openTab(Route.Boards)
        NextDestination.SAVED -> openTab(Route.Saved)
        NextDestination.WATCHED -> openTab(Route.Watched)
        NextDestination.DOWNLOADS -> open(Route.Downloads)
        NextDestination.SETTINGS -> open(Route.Settings)
    }
}

@Composable
internal fun ThreadCollectionsDestination(
    browser: Browser,
    route: Route,
) {
    val saved by browser.savedThreads.collectAsState(emptyList())
    val history by browser.readingHistory.collectAsState(emptyList())
    val rows =
        when (route) {
            Route.History ->
                history.sortedByDescending { it.lastVisitedMillis }.map {
                    it.key to
                        ThreadCollectionRow(
                            it.key.toString(),
                            it.title,
                            "${browser.siteName(it.key.provider)} · /${it.key.board.value}/",
                        )
                }
            else ->
                saved.filter { route != Route.Watched || it.isWatched }.sortedByDescending { it.createdAtMillis }.map {
                    it.key to
                        ThreadCollectionRow(
                            it.key.toString(),
                            it.title,
                            "${browser.siteName(it.key.provider)} · /${it.key.board.value}/",
                        )
                }
        }
    val title =
        stringResource(
            when (route) {
                Route.History -> Res.string.ios_collection_history
                Route.Watched -> Res.string.ios_collection_watched
                else -> Res.string.ios_collection_saved
            },
        )
    val byRow = rows.associate { it.second.id to it.first }
    val isTab = route == Route.Saved || route == Route.Watched
    ThreadCollectionScreen(
        title = title,
        rows = rows.map { it.second },
        onOpen = { row -> byRow[row.id]?.let(browser::openThread) },
        onBack =
            if (isTab) {
                null
            } else {
                { browser.back() }
            },
        onSettings =
            if (isTab) {
                { browser.open(Route.Settings) }
            } else {
                null
            },
        onDestination = if (isTab) browser::openDestination else null,
        selectedDestination = if (route == Route.Watched) NextDestination.WATCHED else NextDestination.SAVED,
    )
}

/** A network failure must not remove the tab bar or trap the reader on a loading screen. */
@Composable
private fun <T> PrimaryLoaded(
    browser: Browser,
    load: Load<T>,
    title: String,
    destination: NextDestination,
    content: @Composable (T) -> Unit,
) {
    when (load) {
        is Load.Ready -> content(load.value)
        else ->
            MessageScreen(
                title = title,
                subtitle = if (load is Load.Failed) load.message else stringResource(Res.string.ios_primary_loading),
                skeleton = load is Load.Loading,
                actionLabel = if (load is Load.Failed) stringResource(Res.string.ios_primary_retry) else null,
                onAction = browser::retry,
                destination = destination,
                onDestination = browser::openDestination,
            )
    }
}
