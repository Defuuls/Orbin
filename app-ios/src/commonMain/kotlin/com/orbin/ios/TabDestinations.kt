package com.orbin.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.Board
import com.orbin.core.model.CatalogMode
import com.orbin.core.model.DohProvider
import com.orbin.core.model.FormFactor
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.ProviderId
import com.orbin.core.model.SharpPreviewsMode
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.core.model.isWideWindow
import com.orbin.core.model.shouldLoadSharpPreview
import com.orbin.core.ui.date.formatPostDateTime
import com.orbin.domain.bookmarks.collectionUnread
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_boards_no_visible
import com.orbin.ios.resources.ios_boards_source_unavailable
import com.orbin.ios.resources.ios_collection_all_sites
import com.orbin.ios.resources.ios_collection_history
import com.orbin.ios.resources.ios_collection_saved
import com.orbin.ios.resources.ios_collection_watched
import com.orbin.ios.resources.ios_feed_favorites
import com.orbin.ios.resources.ios_feed_favorites_empty_message
import com.orbin.ios.resources.ios_feed_favorites_empty_title
import com.orbin.ios.resources.ios_primary_loading
import com.orbin.ios.resources.ios_search_follow_boards
import com.orbin.uinext.BoardsScreen
import com.orbin.uinext.FeedScreen
import com.orbin.uinext.LibrarySection
import com.orbin.uinext.LocalOrbinNavigationAtSide
import com.orbin.uinext.NextDestination
import com.orbin.uinext.NextPullToRefresh
import com.orbin.uinext.NextScaffold
import com.orbin.uinext.OpenThreadLinkDialog
import com.orbin.uinext.PlatformSegments
import com.orbin.uinext.SearchScreen
import com.orbin.uinext.SearchState
import com.orbin.uinext.SettingsScreen
import com.orbin.uinext.ThreadCollectionRow
import com.orbin.uinext.ThreadCollectionScreen
import com.orbin.uinext.ThreadCollectionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import com.orbin.uinext.CatalogMode as ReaderCatalogMode

private const val CLEAR_ARMED_MS = 4_000L

// Home, Boards, Watched, Search and Saved drawer destinations, plus their detail pages.

@Composable
internal fun FeedDestination(
    browser: Browser,
    downloads: MediaDownloads? = null,
) {
    val feed by browser.feed.collectAsState()
    val boards by browser.boards.collectAsState()
    val settings by browser.settings.current.collectAsState()
    val isMetered by browser.networkMonitor.isMetered.collectAsState(true)
    val isDataSaver by browser.networkMonitor.isDataSaver.collectAsState(false)
    val downloadBadge = rememberActiveDownloadBadge(downloads)
    val site by browser.feedSite.collectAsState()
    val favoritesOnly by browser.feedFavoritesOnly.collectAsState()
    val favoriteBoards by browser.favorites.collectAsState()
    val followedBoards by browser.followed.collectAsState()
    val scopeBoards = if (favoritesOnly) favoriteBoards else followedBoards
    val loadingMore by browser.feedLoadingMore.collectAsState()
    val canLoadMore by browser.feedCanLoadMore.collectAsState()
    val discoveryProgress by browser.feedDiscoveryProgress.collectAsState()
    val contentPolicy by browser.feedContentPolicy.collectAsState()
    val saved by browser.savedThreads.collectAsState(emptyList())
    val visited by remember { browser.visitedKeys() }.collectAsState(emptySet())
    val policyMatches =
        contentPolicy ==
            HomeFeedContentPolicy(settings.nsfwContentHidden, settings.coverViolentMedia, settings.coverNsfwMedia)
    val threads =
        remember(feed, policyMatches, scopeBoards) {
            if (policyMatches) {
                (feed as? Load.Ready)?.value.orEmpty().filter {
                    FollowedBoard(it.thread.key.provider, it.thread.key.board) in
                        scopeBoards
                }
            } else {
                emptyList()
            }
        }
    val now = remember(threads) { Clock.System.now().toEpochMilliseconds() }
    // Caption text and activity belong to the content snapshot; local membership only changes flags.
    val baseRows = remember(threads) { threads.map { it.thread.key to it.toFeedRow(now, read = false) } }
    val byKey = remember(saved) { saved.associateBy { it.key } }
    val rows =
        remember(baseRows, visited, byKey) {
            baseRows.map { (key, row) ->
                row.copy(
                    read = key in visited,
                    watched = byKey[key]?.isWatched == true,
                    bookmarked = key in byKey,
                )
            }
        }
    val byRow = remember(threads) { threads.associateBy { it.feedRowId } }
    NextPullToRefresh(
        isRefreshing = feed is Load.Loading,
        onRefresh = browser::retry,
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints {
            val catalogMode =
                when (settings.catalogMode) {
                    CatalogMode.LIST -> ReaderCatalogMode.LIST
                    CatalogMode.GRID -> ReaderCatalogMode.GRID
                    CatalogMode.CARD -> ReaderCatalogMode.CARD
                }
            FeedScreen(
                rows = rows,
                emptyTitle = stringResource(Res.string.ios_feed_favorites_empty_title).takeIf { favoritesOnly },
                emptyMessage = stringResource(Res.string.ios_feed_favorites_empty_message).takeIf { favoritesOnly },
                mode = catalogMode,
                downloadBadge = downloadBadge,
                onModeChange = { selected ->
                    browser.settings.setCatalogMode(
                        when (selected) {
                            ReaderCatalogMode.LIST -> CatalogMode.LIST
                            ReaderCatalogMode.GRID -> CatalogMode.GRID
                            ReaderCatalogMode.CARD -> CatalogMode.CARD
                        },
                    )
                },
                wideWindow = isWideWindow(maxWidth.value.toInt(), maxHeight.value.toInt()),
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
                onOpenRow = { row -> byRow[row.id]?.let { browser.openThread(it.thread.key) } },
                onOpenMedia = { row ->
                    byRow[row.id]?.let { entry ->
                        entry.thread.originalPost.attachments.firstOrNull()?.let { attachment ->
                            browser.openMedia(entry.thread.key, attachment)
                        }
                    }
                },
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
                thumbnail = { row, modifier ->
                    byRow[row.id]?.thread?.originalPost?.attachments?.firstOrNull()?.let { attachment ->
                        // The reader cell reveals spoilers before requesting this bounded preview.
                        ReaderThumbnail(
                            attachment.copy(isSpoiler = false),
                            modifier,
                            sharp =
                                shouldLoadSharpPreview(
                                    modeWantsSharp = catalogMode.sharpensThumbnails,
                                    setting = settings.sharpPreviews,
                                    isMetered = isMetered,
                                    isDataSaver = isDataSaver,
                                    mediaType = attachment.type,
                                    sizeBytes = attachment.sizeBytes,
                                ),
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
                onOpenDownloads = { browser.openLibrary(LibrarySection.DOWNLOADS) },
                onOpenSearch = { browser.open(Route.Search) },
                onOpenSaved = { browser.openLibrary(LibrarySection.SAVED) },
                onOpenWatched = { browser.openLibrary(LibrarySection.WATCHED) },
                onOpenLibrary = { browser.openLibrary() },
                watchedBadge = rememberWatchedBadge(browser),
                onSettings = { browser.openPrimaryPage(Route.Settings) },
                sourceLabels =
                    listOf(
                        stringResource(Res.string.ios_collection_all_sites),
                        stringResource(Res.string.ios_feed_favorites),
                    ) +
                        browser.sites.map { it.name },
                selectedSource =
                    if (favoritesOnly) {
                        1
                    } else if (site ==
                        null
                    ) {
                        0
                    } else {
                        browser.sites.indexOfFirst { it.id == site } + 2
                    },
                onSelectSource = { index ->
                    if (index == 1) {
                        browser.selectFeedFavoritesOnly(true)
                    } else {
                        browser.selectFeedSite(browser.sites.getOrNull(index - 2)?.id)
                    }
                },
                uniformCards = true,
            )
        }
    }
}

@Composable
internal fun BoardsDestination(
    browser: Browser,
    downloads: MediaDownloads? = null,
) {
    var linkDialog by rememberSaveable { mutableStateOf(false) }
    val downloadBadge = rememberActiveDownloadBadge(downloads)
    val directory by browser.boardDirectory.collectAsState()
    val followed by browser.followed.collectAsState()
    val favorites by browser.favorites.collectAsState()
    val unreachable by browser.unreachableSites.collectAsState()
    val site by browser.activeSite.collectAsState()
    val settings by browser.settings.current.collectAsState()
    val boards = remember(directory, site) { directory.boardsOnSite(site) }
    // The selector remains available while the selected source loads or fails.
    val list =
        remember(boards, settings.nsfwContentHidden) {
            (boards as? Load.Ready)?.value.orEmpty().filterNot { settings.nsfwContentHidden && it.board.isNsfw }
        }
    val byTile = remember(list) { list.associateBy { it.tileId } }
    BoardsScreen(
        onOpenLink = { linkDialog = true },
        downloadBadge = downloadBadge,
        hideRailOnScroll = true,
        subtitle = boardsSubtitle(list.size, unreachable),
        emptyMessage =
            when (boards) {
                Load.Loading -> stringResource(Res.string.ios_primary_loading)
                is Load.Failed -> stringResource(Res.string.ios_boards_source_unavailable, browser.siteName(site))
                is Load.Ready -> stringResource(Res.string.ios_boards_no_visible)
            },
        onRetry = if (site in directory.failures) browser::retry else null,
        boards =
            remember(list, followed, favorites) {
                list.map {
                    it.toTile(
                        followed = FollowedBoard(it.provider, it.board.id) in followed,
                        favorite = FollowedBoard(it.provider, it.board.id) in favorites,
                    )
                }
            },
        onOpenBoard = { tile -> byTile[tile.id]?.let(browser::openBoard) },
        onFollowBoard = { tile, follow -> byTile[tile.id]?.let { browser.setFollowed(it, follow) } },
        onFavoriteBoard = { tile, favorite -> byTile[tile.id]?.let { browser.setFavorite(it, favorite) } },
        onOpenFeed = { browser.openTab(Route.Feed) },
        onOpenDownloads = { browser.openLibrary(LibrarySection.DOWNLOADS) },
        onOpenSaved = { browser.openLibrary(LibrarySection.SAVED) },
        onOpenWatched = { browser.openLibrary(LibrarySection.WATCHED) },
        onOpenLibrary = { browser.openLibrary() },
        watchedBadge = rememberWatchedBadge(browser),
        onOpenSearch = { browser.open(Route.Search) },
        onOpenSettings = { browser.openPrimaryPage(Route.Settings) },
        headerContent = { SiteSwitcher(browser, site, gapAfter = 0.dp, compact = true) },
    )
    if (linkDialog) {
        OpenThreadLinkDialog(
            onDismiss = { linkDialog = false },
            onOpen = { text ->
                browser.parseThreadLink(text)?.let { key ->
                    browser.openThread(key)
                    true
                } ?: false
            },
        )
    }
}

/** Board directory source selector. Home adds an All sites choice separately. */
@Composable
private fun SiteSwitcher(
    browser: Browser,
    active: ProviderId,
    // Boards puts its search field on the same row as this compact provider menu.
    gapAfter: Dp = 0.dp,
    compact: Boolean = false,
) {
    if (browser.sites.size < 2) return
    PlatformSegments(
        labels = browser.sites.map { it.name },
        selected = browser.sites.indexOfFirst { it.id == active },
        onSelect = { browser.selectSite(browser.sites[it].id) },
        compact = compact,
    )
    if (gapAfter > 0.dp) {
        Spacer(Modifier.height(gapAfter))
    }
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
        libraryContent =
            if (downloads.mediaLibrary != null) {
                { SavedMediaLibraryContent(browser, downloads) }
            } else {
                null
            },
        bottomBar = null,
        onRetry = downloads::retry,
        onClear = downloads::clear,
        onBack = { if (!browser.back()) browser.openTab(Route.Feed) },
        onDestination = browser::openPrimaryDestination,
    )
}

@Composable
internal fun SettingsDestination(
    browser: Browser,
    lock: AppLock,
    backup: IosBackup,
    formFactor: FormFactor,
    version: AppVersion,
    downloads: MediaDownloads? = null,
    cloudSync: CloudSyncController? = null,
) {
    val settings by browser.settings.current.collectAsState()
    val cloudState = cloudSync?.state?.collectAsState()?.value
    var cloudDeleteArmed by remember { mutableStateOf(false) }
    LaunchedEffect(cloudDeleteArmed) {
        if (cloudDeleteArmed) {
            delay(CLEAR_ARMED_MS)
            cloudDeleteArmed = false
        }
    }
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
    var mediaCacheBytes by remember { mutableStateOf(0L) }
    var mediaCacheWorking by remember { mutableStateOf(false) }
    var mediaCacheFailure by remember { mutableStateOf(false) }

    suspend fun refreshMediaCacheUsage() {
        mediaCacheBytes =
            (imageLoader.memoryCache?.size ?: 0L) + (imageLoader.diskCache?.size ?: 0L) + nativeVideoCacheUsageBytes()
    }
    LaunchedEffect(imageLoader) { refreshMediaCacheUsage() }
    val mediaCacheLabel =
        mediaCacheSizeLabel(mediaCacheBytes) +
            when {
                mediaCacheWorking -> " · Clearing…"
                mediaCacheFailure -> " · Could not clear all files"
                else -> ""
            }
    SettingsScreen(
        subtitle = version.name,
        bottomBar =
            if (LocalOrbinNavigationAtSide.current) {
                null
            } else {
                { PrimaryNavigation(browser, downloads, NextDestination.SETTINGS) }
            },
        groups =
            remember(
                settings,
                clearArmed,
                imageCacheCleared,
                mediaCacheLabel,
                backupState,
                formFactor,
                version,
                playbackScope,
                playbackChoices,
                cloudState,
                cloudDeleteArmed,
            ) {
                settingsGroups(
                    settings,
                    clearArmed,
                    imageCacheCleared,
                    backupState,
                    version,
                    cloudSyncRows = cloudState?.let { cloudSyncRows(it, cloudDeleteArmed) }.orEmpty(),
                    mediaCacheLabel = mediaCacheLabel,
                ) +
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
                "watched" -> browser.openLibrary(LibrarySection.WATCHED)
                "downloads" -> browser.openLibrary(LibrarySection.DOWNLOADS)
                "help" -> uriHandler.openUri("https://github.com/Defuuls/Orbin/wiki")
            }
        },
        onActivate = { item ->
            if (!browser.settings.activatePlayback(item.id, settings, playbackScope) &&
                !browser.settings.activatePrivacy(item.id, settings)
            ) {
                when (item.id) {
                    "cloudSyncEnable" -> cloudSync?.setEnabled(cloudState?.enabled != true)
                    "cloudSyncNow" -> cloudSync?.syncNow()
                    "cloudSyncDelete" -> {
                        if (cloudDeleteArmed) {
                            cloudDeleteArmed = false
                            cloudSync?.deleteCloudData()
                        } else {
                            cloudDeleteArmed = true
                        }
                    }
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
                    SettingIds.THEME,
                    SettingIds.FEED_COLUMNS,
                    SettingIds.CATALOG_MODE,
                    "hiddenThreadPatterns",
                    "postFilterRules",
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
                    SettingIds.CLEAR_MEDIA_CACHE ->
                        if (!mediaCacheWorking) {
                            scope.launch {
                                mediaCacheWorking = true
                                mediaCacheFailure = false
                                try {
                                    imageLoader.memoryCache?.clear()
                                    withContext(Dispatchers.IO) { imageLoader.diskCache?.clear() }
                                } catch (failure: kotlinx.coroutines.CancellationException) {
                                    throw failure
                                } catch (_: Exception) {
                                    mediaCacheFailure = true
                                }
                                try {
                                    clearNativeVideoCache()
                                } catch (failure: kotlinx.coroutines.CancellationException) {
                                    throw failure
                                } catch (_: Exception) {
                                    mediaCacheFailure = true
                                } finally {
                                    refreshMediaCacheUsage()
                                    mediaCacheWorking = false
                                }
                                if (!mediaCacheFailure) Haptics.success()
                            }
                        }
                    SettingIds.CLEAR_IMAGE_CACHE ->
                        scope.launch {
                            imageLoader.memoryCache?.clear()
                            withContext(Dispatchers.IO) { imageLoader.diskCache?.clear() }
                            imageCacheCleared = true
                            refreshMediaCacheUsage()
                            Haptics.success()
                        }
                }
            }
        },
        onCommitFilters = { item, rules -> browser.settings.commitFilter(item.id, rules) },
        onSelectOption = { item, index ->
            Haptics.light()
            if (!browser.settings.selectPlaybackChoice(item.id, index, settings, playbackScope) &&
                !browser.settings.selectAppearance(item.id, index)
            ) {
                when (item.id) {
                    "playbackScope" -> playbackChoices.getOrNull(index)?.let { playbackScope = it.first }
                    SettingIds.DOH_PROVIDER ->
                        DohProvider.entries
                            .getOrNull(
                                index,
                            )?.let(browser.settings::setDohProvider)
                    SettingIds.THEME -> AppThemeMode.entries.getOrNull(index)?.let(browser.settings::setThemeMode)
                    SettingIds.FEED_COLUMNS -> browser.settings.setFeedColumns(formFactor, index + 1)
                    SettingIds.CATALOG_MODE ->
                        CatalogMode.entries.getOrNull(index)?.let(browser.settings::setCatalogMode)
                    SettingIds.SHARP_PREVIEWS ->
                        SharpPreviewsMode.entries.getOrNull(index)?.let(browser.settings::setSharpPreviews)
                }
            }
            expanded = null
        },
    )
}

@Composable
internal fun SearchDestination(browser: Browser) {
    val webProvider =
        remember(browser) {
            com.orbin.provider.api
                .fourchanWebSearchProvider(browser.webSearchProviders)
        }
    val webDirectory by browser.boardDirectory.collectAsState()
    val webBoards =
        remember(webDirectory, webProvider) {
            webDirectory.boards.filter { it.provider == webProvider?.id }.map { it.board }
        }
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
        onBack = { if (!browser.back()) browser.openTab(Route.Feed) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            SearchScreen(
                onWebSearch = browser.webSearchSession?.let { it::submit },
                webSearch =
                    if (webProvider != null) {
                        { submitted ->
                            browser.webSearchTransport?.let { transport ->
                                FourchanWebSearch(submitted, transport, webBoards, browser::openThread)
                            }
                        }
                    } else {
                        null
                    },
                query = query,
                onQueryChange = browser.search::setQuery,
                onSearch = { browser.search.run() },
                onBack = { if (!browser.back()) browser.openTab(Route.Feed) },
                state = state,
                onOpenRow = { row -> byRow[row.id]?.let { browser.openThread(it.thread.key) } },
            )
        }
    }
}

internal fun Browser.openDestination(destination: NextDestination) {
    when (destination) {
        NextDestination.SETTINGS -> open(Route.Settings)
        else -> openPrimaryDestination(destination)
    }
}

@Composable
internal fun ThreadCollectionsDestination(
    browser: Browser,
    route: Route,
) {
    val saved by browser.savedThreads.collectAsState(emptyList())
    val history by browser.readingHistory.collectAsState(emptyList())
    val settings by browser.settings.current.collectAsState()
    val boardState by browser.boards.collectAsState()
    val directory = (boardState as? Load.Ready)?.value.orEmpty().associateBy { it.provider to it.board.id }

    fun visible(
        key: com.orbin.core.model.ThreadKey,
        title: String,
    ): Boolean {
        val board = directory[key.provider to key.board]?.board
        return !com.orbin.core.model.PermanentContentFilter
            .matchesAny(listOf(key.board.value, title)) &&
            board?.isPermanentlyFiltered() != true &&
            (!settings.nsfwContentHidden || board?.isNsfw == false)
    }
    val memory by browser.threadMemory.observeCurrent().collectAsState(emptyList())
    val refreshing by browser.collections.isRefreshing.collectAsState()
    val memoryByKey = memory.associateBy { it.key }
    androidx.compose.runtime.LaunchedEffect(route) { if (route != Route.History) browser.collections.refreshStatuses() }
    val rows =
        if (route == Route.History) {
            history
                .filter {
                    visible(it.key, it.title)
                }.sortedBy { if (settings.collectionNewestFirst) -it.lastVisitedMillis else it.lastVisitedMillis }
                .map {
                    it.key to
                        ThreadCollectionRow(
                            it.key.toString(),
                            it.title,
                            "${browser.siteName(it.key.provider)} · /${it.key.board.value}/",
                            time = formatPostDateTime(it.lastVisitedMillis).orEmpty(),
                            threadNumber =
                                it.key.thread.value
                                    .toString(),
                            unread =
                                saved
                                    .firstOrNull { bookmark ->
                                        bookmark.key == it.key
                                    }?.collectionUnread(memoryByKey[it.key])
                                    ?: memoryByKey[it.key]?.collectionUnread() ?: 0,
                        )
                }
        } else {
            saved
                .filter { visible(it.key, it.title) && (route != Route.Watched || it.isWatched) }
                .sortedBy { if (settings.collectionNewestFirst) -it.createdAtMillis else it.createdAtMillis }
                .map {
                    it.key to
                        ThreadCollectionRow(
                            it.key.toString(),
                            it.title,
                            "${browser.siteName(it.key.provider)} · /${it.key.board.value}/",
                            time = formatPostDateTime(it.createdAtMillis).orEmpty(),
                            threadNumber =
                                it.key.thread.value
                                    .toString(),
                            unread = it.collectionUnread(memoryByKey[it.key]),
                            availability = ThreadCollectionStatus.valueOf(it.availability.name),
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
    val bookmarkCollection = route != Route.History
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
        newestFirst = settings.collectionNewestFirst,
        refreshing = refreshing,
        onRefresh = { browser.collections.refreshStatuses(true) }.takeIf { bookmarkCollection },
        onToggleOrder = { browser.settings.setCollectionNewestFirst(!settings.collectionNewestFirst) },
        onRemove = { row -> byRow[row.id]?.let { browser.collections.remove(it, !bookmarkCollection) } },
        onMarkRead =
            { row: ThreadCollectionRow ->
                byRow[row.id]?.let { browser.collections.markRead(it) }
                Unit
            }.takeIf { bookmarkCollection },
        onMarkAllRead = { browser.collections.markAllRead(route == Route.Watched) }.takeIf { bookmarkCollection },
        onClear = { browser.collections.clear(!bookmarkCollection, route == Route.Watched) },
        onRemoveDeleted = { browser.collections.removeDeleted(route == Route.Watched) }.takeIf { bookmarkCollection },
    )
}
