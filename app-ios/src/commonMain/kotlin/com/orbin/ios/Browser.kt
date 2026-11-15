package com.orbin.ios

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogSort
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.HomeFeedMode
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.PostId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadKey
import com.orbin.core.reader.ThreadPresentationIndex
import com.orbin.core.reader.preparePresentation
import com.orbin.data.database.dao.DownloadDao
import com.orbin.domain.filter.ContentPolicy
import com.orbin.domain.notification.ThreadNotifier
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.provider.api.EngineKind
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.isVisibleInOrbin
import com.orbin.uinext.FeedRow
import com.orbin.uinext.LibrarySection
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** Something being fetched: still coming, arrived, or failed with a message to show. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>

    data class Ready<T>(
        val value: T,
    ) : Load<T>

    data class Failed(
        val message: String,
        val cause: BrowserFailureCause = BrowserFailureCause.UNKNOWN,
        val contentBlocked: Boolean = false,
    ) : Load<Nothing>
}

/** Stable causes for reader state titles; the provider's detail remains in [Load.Failed.message]. */
enum class BrowserFailureCause { NETWORK, NOT_FOUND, SERVER, UNKNOWN }

/** A board together with the site it belongs to; board ids are only unique within a site. */
data class SiteBoard(
    val provider: ProviderId,
    val siteName: String,
    val board: Board,
)

/** A thread in the merged feed, with the site it came from. */
data class FeedThread(
    val provider: ProviderId,
    val thread: CatalogThread,
)

/** Where the reader is, with a drawer destination at the bottom of the back stack. */
sealed interface Route {
    /** The newest threads of every followed board, one list: the start screen, as on Android. */
    data object Feed : Route

    data object Boards : Route

    /** Downloaded files, opened from Settings and activity or a legacy quick action. */
    data object Downloads : Route

    /** Bookmarked threads across every site. */
    data object Saved : Route

    data object History : Route

    data object Watched : Route

    /** Search over explicitly subscribed boards across sites. */
    data object Search : Route

    /** Settings, opened from the feed's header or the boards list. */
    data object Settings : Route

    data class Catalog(
        val board: SiteBoard,
    ) : Route

    /** Every file in a board's catalog, as one wall: the catalog's "media" action, as on Android. */
    data class BoardMedia(
        val board: SiteBoard,
    ) : Route

    data class ThreadPage(
        val key: ThreadKey,
    ) : Route

    /** Videos from every post in a thread, opened directly from Home's chat action. */
    data class ThreadVideos(
        val key: ThreadKey,
    ) : Route

    /** Full-screen media, optionally anchored by the catalog's attachment and thumbnail identity. */
    data class Media(
        val thread: ThreadKey,
        val index: Int,
        val attachmentId: String? = null,
        val thumbnailUrl: String? = null,
        /** The exact tapped file can start before its thread's remaining files arrive. */
        val initialAttachment: MediaAttachment? = null,
        val mediaFilter: com.orbin.uinext.MediaKindFilter = com.orbin.uinext.MediaKindFilter.ALL,
    ) : Route
}

/** The board whose catalog a route shows: its page, or its media wall. */
private val Route.catalogBoard: SiteBoard?
    get() =
        when (this) {
            is Route.Catalog -> board
            is Route.BoardMedia -> board
            else -> null
        }

/**
 * The iOS app's navigation and loading state: boards from every site, then a board's catalog,
 * then a thread. The screens are the shared `ui-next` ones; this holds what Android keeps in its
 * ViewModels and navigation graph, in the few lines a read-only first version needs.
 *
 * Watching a thread and the record of which threads were read go to the shared database through
 * the same repositories Android uses, so they behave the same on both platforms; [watched] holds
 * the watch side.
 *
 * Each destination loads when it is opened and again on [retry]. Going back to a page that already
 * loaded shows it as it was rather than fetching it again. Starting a new load cancels the one
 * still running, so a slow thread cannot overwrite the one the reader opened next.
 * Navigation and loading actions share one back stack, loader cache, and app-lifetime scope.
 */
@Suppress("TooManyFunctions")
class Browser(
    private val providers: List<ImageBoardProvider>,
    private val bookmarks: BookmarkRepository,
    private val history: HistoryRepository,
    private val boardPreferences: BoardPreferencesRepository,
    settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    notifier: ThreadNotifier? = null,
    onWatch: () -> Unit = {},
    downloadDao: DownloadDao? = null,
    private val readerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val threadMemory: com.orbin.domain.repository.ThreadMemoryRepository =
        com.orbin.domain.repository
            .InMemoryThreadMemoryRepository(),
    private val threadCopies: com.orbin.domain.repository.SavedThreadRepository? = null,
) {
    private val byId = providers.associateBy { it.metadata.id }

    /** The reader's settings; hiding NSFW boards shapes the boards list, the feed and search. */
    val settings = ReaderSettings(settings, history, scope, downloadDao, threadMemory = threadMemory)

    private val followedBoards: Flow<Set<FollowedBoard>> =
        combine(
            providers.map { provider ->
                val id = provider.metadata.id
                boardPreferences.observeSubscribedBoards(id).map { subscribed ->
                    subscribed.map { FollowedBoard(id, it) }
                }
            },
        ) { perSite -> perSite.flatMap { it }.toSet() }

    /** Every followed board, on every site: what the feed is made of and the boards list marks. */
    val followed: StateFlow<Set<FollowedBoard>> =
        followedBoards.stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** Setup and Home read explicit persisted subscriptions, never independent favorites. */
    val firstRun =
        FirstRun(
            settings,
            followedBoards.map { it.isNotEmpty() },
            scope,
        )

    /** Every site, in the order the app registers them: the site switcher's segments. */
    val sites: List<Site> = providers.map { Site(it.metadata.id, it.metadata.displayName) }
    val webSearchProviders = providers.map { it.metadata }

    /** Search results and verification outlive a covered Search screen. */
    internal val webSearchSession by lazy {
        com.orbin.provider.api.fourchanWebSearchProvider(webSearchProviders)?.let { provider ->
            com.orbin.provider.api
                .FourchanSearchSession(provider) { hit ->
                    hit.isVisibleInOrbin(
                        this.settings.current.value,
                        boardDirectory.value.boards
                            .filter { it.provider == provider.id }
                            .map { it.board },
                    )
                }.also { session ->
                    scope.launch {
                        combine(
                            this@Browser.settings.current,
                            boardDirectory,
                        ) { _, _ -> Unit }.collect { session.refilter() }
                    }
                }
        }
    }
    internal val webSearchTransport by lazy {
        webSearchSession?.let { session ->
            NativeSearchTransport(session, scope).also { transport ->
                scope.coroutineContext[Job]?.invokeOnCompletion { transport.close() }
            }
        }
    }

    /** Resolve known site identities; arbitrary pasted URLs never reach the HTTP client. */
    fun parseThreadLink(text: String): ThreadKey? =
        com.orbin.provider.api
            .parseThreadLink(text, providers.map { it.metadata })
            ?.key

    /**
     * The board directory's saved source. Home has an independent filter, initially All sites.
     */
    val activeSite: StateFlow<ProviderId> =
        this.settings.current
            .map { current -> sites.firstOrNull { it.id.value == current.activeProviderId }?.id ?: sites.first().id }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, sites.first().id)

    /** Shows [site]'s board directory and remembers it. */
    fun selectSite(site: ProviderId) {
        if (site != activeSite.value) this.settings.setActiveProviderId(site)
    }

    /** Home's source filter is independent of the board directory's selected site. */
    private val _feedSite = MutableStateFlow<ProviderId?>(null)
    val feedSite: StateFlow<ProviderId?> = _feedSite.asStateFlow()
    private val _feedFavoritesOnly = MutableStateFlow(false)
    val feedFavoritesOnly: StateFlow<Boolean> = _feedFavoritesOnly.asStateFlow()

    fun selectFeedFavoritesOnly(value: Boolean) {
        if (value) _feedSite.value = null
        _feedFavoritesOnly.value = value
    }

    fun selectFeedSite(site: ProviderId?) {
        require(site == null || site in byId) { "Unknown site: $site" }
        _feedFavoritesOnly.value = false
        if (_feedSite.value == site) return
        _feedSite.value = site
        loadFeed()
    }

    val feedMode: StateFlow<HomeFeedMode> get() = homeFeed.mode
    val feedLoadingMore: StateFlow<Boolean> get() = homeFeed.isLoadingMore
    val feedCanLoadMore: StateFlow<Boolean> get() = homeFeed.canLoadMore
    val feedDiscoveryProgress: StateFlow<Int> get() = homeFeed.completedDiscoveryCount
    val feedContentPolicy: StateFlow<HomeFeedContentPolicy> get() = homeFeed.contentPolicy

    fun selectFeedMode(mode: HomeFeedMode) = homeFeed.selectMode(mode)

    fun loadMoreFeed() = homeFeed.loadMore()

    internal val collections =
        LocalThreadCollections(
            bookmarks,
            scope,
            now,
            onWatch,
            providers,
            history,
            threadMemory,
            preferences = { this.settings.current.value },
        )
    val savedThreads = bookmarks.observeBookmarks()
    val readingHistory = history.observeHistory()

    /** Reader visibility, rather than merely loading a response, clears watched unread counts. */
    fun markThreadRead(key: ThreadKey) {
        scope.launch {
            val latestReplies = bookmarks.getBookmark(key)?.latestReplyCount ?: 0
            threadMemory.update(key) {
                it.copy(acknowledgedReplyCount = maxOf(it.acknowledgedReplyCount, latestReplies))
            }
            if (!settings.current.value.historySessionOnly) bookmarks.markRead(key)
        }
    }

    /** Searching the followed boards' catalogs. */
    val search =
        ThreadSearch(scope, boards = ::searchableBoards) { board ->
            provider(board.provider).getCatalog(CatalogRequest(board.provider, board.board))
        }

    /** Watching threads, their unread counts, and keeping those counts current. */
    val watched =
        WatchedThreads(bookmarks, scope, now, notifier, onWatch, threadMemory = threadMemory) { key ->
            provider(key.provider).getThread(key.board, key.thread)
        }

    private val _backStack = MutableStateFlow<List<Route>>(listOf(Route.Feed))
    val backStack: StateFlow<List<Route>> = _backStack.asStateFlow()
    private val _pendingThreadPost = MutableStateFlow<Pair<ThreadKey, PostId>?>(null)
    val pendingThreadPost: StateFlow<Pair<ThreadKey, PostId>?> = _pendingThreadPost.asStateFlow()

    fun clearPendingThreadPost(expected: Pair<ThreadKey, PostId>) {
        _pendingThreadPost.compareAndSet(expected, null)
    }

    private val directoryLoader = BrowserBoardDirectory(providers, scope)
    private val _boards = directoryLoader.boards
    val boards: StateFlow<Load<List<SiteBoard>>> = _boards.asStateFlow()

    private val _unreachableSites = directoryLoader.unreachable
    private val feedDirectory = directoryLoader.directory

    /** Per-source directory progress lets a ready source remain usable while another loads. */
    internal val boardDirectory: StateFlow<HomeFeedDirectory> = feedDirectory.asStateFlow()

    /**
     * The sites whose boards failed to load while others loaded, each with the reason, so the
     * Boards screen can say so rather than the site silently missing from the list.
     */
    val unreachableSites: StateFlow<List<String>> = _unreachableSites.asStateFlow()

    private val _catalog = MutableStateFlow<Load<List<CatalogThread>>>(Load.Loading)
    val catalog: StateFlow<Load<List<CatalogThread>>> =
        filterReaderCatalog(_catalog, this.settings.current, readerDispatcher)
            .stateIn(scope, SharingStarted.Eagerly, Load.Loading)
    private val _catalogRefreshing = MutableStateFlow(false)
    val catalogRefreshing: StateFlow<Boolean> = _catalogRefreshing.asStateFlow()
    private val _catalogRefreshFailure = MutableStateFlow<Load.Failed?>(null)
    val catalogRefreshFailure: StateFlow<Load.Failed?> = _catalogRefreshFailure.asStateFlow()
    private val _preparedCatalogRows = MutableStateFlow<Map<ThreadKey, FeedRow>>(emptyMap())
    private val preparedCatalogSource = MutableStateFlow<List<CatalogThread>?>(null)
    val preparedCatalogRows: StateFlow<Map<ThreadKey, FeedRow>> = _preparedCatalogRows.asStateFlow()

    private val _catalogSort = MutableStateFlow(CatalogSort.BUMP_ORDER)

    /** How the open catalog is ordered: bump order for each board opened, as on Android. */
    val catalogSort: StateFlow<CatalogSort> = _catalogSort.asStateFlow()

    /** The catalog's next order, as Android's sort chip steps through them. */
    fun cycleCatalogSort() {
        _catalogSort.update { it.next() }
    }

    fun setCatalogSort(sort: CatalogSort) {
        _catalogSort.value = sort
    }

    private val _thread = MutableStateFlow<Load<Thread>>(Load.Loading)
    val thread: StateFlow<Load<Thread>> = _thread.asStateFlow()
    private val _threadRefreshing = MutableStateFlow(false)
    val threadRefreshing: StateFlow<Boolean> = _threadRefreshing.asStateFlow()
    private val _threadRefreshFailure = MutableStateFlow<Load.Failed?>(null)
    val threadRefreshFailure: StateFlow<Load.Failed?> = _threadRefreshFailure.asStateFlow()
    private val _preparedThread = MutableStateFlow<ThreadPresentationIndex?>(null)
    val preparedThread: StateFlow<ThreadPresentationIndex?> = _preparedThread.asStateFlow()

    internal val mediaScroll =
        MediaScrollSync(
            currentRoute = { _backStack.value.last() },
            currentThread = { (_thread.value as? Load.Ready)?.value },
        )

    private val homeFeed =
        HomeFeed(
            providers = providers,
            preferences = boardPreferences,
            settings = settings.settings,
            followed = followed,
            directory = feedDirectory,
            source = feedSite,
            favoritesOnly = feedFavoritesOnly,
            firstRun = firstRun.state,
            scope = scope,
            isActive = { _backStack.value.last() == Route.Feed },
        )
    val feed: StateFlow<Load<List<FeedThread>>> = homeFeed.content

    private val _firstUnreadPostId = MutableStateFlow<String?>(null)

    /**
     * The first reply the reader had not seen when the open thread loaded, if it is watched and has
     * new replies: where the thread screen's "jump to unread" goes, as on Android.
     */
    val firstUnreadPostId: StateFlow<String?> = _firstUnreadPostId.asStateFlow()

    /** Stars remain independent shortcuts; only explicit subscriptions contribute to Home. */
    val favorites: StateFlow<Set<FollowedBoard>> =
        combine(
            providers.map { provider ->
                val id = provider.metadata.id
                boardPreferences.observeFavoriteBoards(id).map { boards -> boards.map { FollowedBoard(id, it) } }
            },
        ) { perSite -> perSite.flatMap { it }.toSet() }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    fun setFavorite(
        board: SiteBoard,
        favorite: Boolean,
    ) {
        Haptics.light()
        scope.launch { boardPreferences.setFavoriteBoard(board.provider, board.board.id, favorite) }
    }

    private val pageLoader = BrowserPageLoader(scope)
    private val catalogLoad = PageLoad()
    private val threadLoad = PageLoad()
    private var readerLayoutWide = false

    // What the catalog and thread states hold, so returning to them does not fetch them again.
    private var catalogShown: SiteBoard? = null
    private var threadShown: ThreadKey? = null

    init {
        observeCatalogRowRetention(scope, _catalog, catalog, preparedCatalogSource, _preparedCatalogRows)
        loadBoards()
        scope.launch {
            this@Browser
                .settings.current
                .distinctUntilChangedBy { Triple(it.nsfwContentHidden, it.coverViolentMedia, it.coverNsfwMedia) }
                .drop(1)
                .collect {
                    catalogShown = null
                    threadShown = null
                    if (_backStack.value.last() != Route.Feed) loadCurrent(force = true)
                }
        }
        loadFeed()
        watched.refresh()
    }

    /** Switches drawer destination and returns to its root. */
    fun openTab(tab: Route) {
        require(
            tab == Route.Feed ||
                tab == Route.Search ||
                tab == Route.Boards ||
                tab == Route.Watched ||
                tab == Route.Saved,
        ) { "Not a tab: $tab" }
        mediaScroll.clear()
        _backStack.value = listOf(tab)
        cancelHiddenPages()
        if (tab == Route.Feed) loadFeed()
        // A site that could not be reached gets another try, on a new network say.
        if (tab == Route.Boards && _unreachableSites.value.isNotEmpty()) loadBoards()
        watched.refresh()
    }

    /** Follows or unfollows [board], as the boards list's switch does on Android. */
    fun setFollowed(
        board: SiteBoard,
        follow: Boolean,
    ) {
        Haptics.light()
        firstRun.updateFollow {
            boardPreferences.setSubscribedBoard(board.provider, board.board.id, follow)
        }
    }

    /** Starts the initialized feed across all sites after setup's last board edit has persisted. */
    fun completeFirstRun(): Job =
        scope.launch {
            val savedFollows = followedBoards.first()
            if (feedDirectory.value
                    .eligibleFollowing(
                        savedFollows,
                        settings.current.value.nsfwContentHidden,
                    ).isEmpty()
            ) {
                return@launch
            }
            firstRun
                .complete {
                    followed.first { it.isNotEmpty() }
                    _feedSite.value = null
                    _backStack.value = listOf(Route.Feed)
                    cancelHiddenPages()
                    loadFeed(force = true)
                }.join()
        }

    /** Every thread the reader has opened, on any board, for the feed's read state. */
    fun visitedKeys(): Flow<Set<ThreadKey>> = history.observeVisitedKeys()

    /** Opens a page over the current one: search, settings, or a board's media wall. */
    fun open(page: Route) {
        require(
            page == Route.Search ||
                page == Route.Boards ||
                page == Route.Settings ||
                page == Route.Downloads ||
                page == Route.History ||
                page == Route.Watched ||
                page is Route.BoardMedia,
        ) { "Not a page: $page" }
        _backStack.update { it + page }
        loadCurrent()
    }

    /** Switches primary utility pages without growing a Settings/Downloads trail above the reader. */
    fun openPrimaryPage(page: Route) {
        require(page == Route.Settings || page == Route.Downloads) { "Not a primary page: $page" }
        if (_backStack.value.last() == page) return
        mediaScroll.clear()
        _backStack.update { stack ->
            val reader = stack.dropLastWhile { it == Route.Settings || it == Route.Downloads }
            reader.ifEmpty { listOf(Route.Feed) } + page
        }
        loadCurrent()
    }

    /** The Library section the reader last used; the Library tab reopens it. */
    var librarySection: LibrarySection = LibrarySection.DEFAULT
        private set

    /**
     * Opens Library on [section] (the last one used by default). Library sits directly above
     * Feed, so Back returns to Feed as Android's tab back stack does.
     */
    fun openLibrary(section: LibrarySection = librarySection) {
        librarySection = section
        val page = section.route()
        mediaScroll.clear()
        _backStack.value = listOf(Route.Feed, page)
        cancelHiddenPages()
        loadCurrent()
        watched.refresh()
    }

    /** A watched-thread notification: the thread, over Library's Watched section. */
    fun openWatchedThread(key: ThreadKey) {
        openLibrary(LibrarySection.WATCHED)
        openThread(key)
    }

    fun openBoard(board: SiteBoard) {
        if (board != catalogShown) _catalogSort.value = CatalogSort.BUMP_ORDER
        _backStack.update { it + Route.Catalog(board) }
        loadCurrent()
    }

    /** The catalog picker changes its board in place instead of growing a trail of catalogs. */
    fun replaceBoard(board: SiteBoard) {
        if (board != catalogShown) _catalogSort.value = CatalogSort.BUMP_ORDER
        _backStack.update { stack ->
            val previous = stack.getOrNull(stack.lastIndex - 1)
            when {
                stack.lastOrNull() is Route.Catalog -> stack.dropLast(1) + Route.Catalog(board)
                stack.lastOrNull() is Route.ThreadPage && previous is Route.Catalog ->
                    stack.dropLast(2) + Route.Catalog(board)
                else -> stack + Route.Catalog(board)
            }
        }
        loadCurrent()
    }

    fun openThread(key: ThreadKey) = openThread(key, null)

    fun openThread(
        key: ThreadKey,
        postId: PostId?,
    ) {
        _pendingThreadPost.value = postId?.let { key to it }
        mediaScroll.clear()
        _backStack.update { stack ->
            val previous = stack.getOrNull(stack.lastIndex - 1)
            if (stack.lastOrNull() is Route.ThreadPage &&
                (previous is Route.Catalog || previous?.isThreadReaderSource == true)
            ) {
                stack.dropLast(1) + Route.ThreadPage(key)
            } else {
                stack + Route.ThreadPage(key)
            }
        }
        loadCurrent()
    }

    /** Opens thread videos without inserting a comments page beneath the viewer. */
    fun openThreadVideos(key: ThreadKey) {
        mediaScroll.clear()
        _backStack.update { it + Route.ThreadVideos(key) }
        loadCurrent()
    }

    /** Thread numbers on [board] the reader has opened, for the catalog's read state. */
    fun visitedThreads(board: SiteBoard): Flow<Set<Long>> =
        history.observeVisitedThreadIds(board.provider, board.board.id)

    /** Opens the viewer on the open thread's file number [index], counted across all its posts. */
    fun openMedia(index: Int) = openMedia(index, com.orbin.uinext.MediaKindFilter.ALL)

    fun openMedia(
        index: Int,
        mediaFilter: com.orbin.uinext.MediaKindFilter,
    ) {
        val thread = (_backStack.value.last() as? Route.ThreadPage)?.key ?: return
        preparedThread.value
            ?.attachments
            ?.getOrNull(index)
            ?.sourceUrl
            ?.let(::prepareNativeMediaHandoff)
        mediaScroll.clear()
        _backStack.update { it + Route.Media(thread, index, mediaFilter = mediaFilter) }
        cancelHiddenPages()
    }

    /** Opens a selected catalog file directly, with no hidden comments destination beneath it. */
    fun openMedia(
        key: ThreadKey,
        attachmentId: String,
        thumbnailUrl: String? = null,
    ) {
        mediaScroll.clear()
        _backStack.update { it + Route.Media(key, index = 0, attachmentId = attachmentId, thumbnailUrl = thumbnailUrl) }
        loadCurrent()
    }

    /** A known original file need not wait for the thread request before opening its player. */
    fun openMedia(
        key: ThreadKey,
        attachment: MediaAttachment,
    ) {
        prepareNativeMediaHandoff(attachment.sourceUrl)
        mediaScroll.clear()
        _backStack.update {
            it +
                Route.Media(
                    key,
                    index = 0,
                    attachmentId = attachment.id,
                    thumbnailUrl = attachment.thumbnailUrl,
                    initialAttachment = attachment,
                )
        }
        loadCurrent()
    }

    /** Pops one destination. Returns false at a drawer root, where there is nothing to pop. */
    fun back(): Boolean {
        if (_backStack.value.size <= 1) return false
        _backStack.update { it.dropLast(1) }
        loadCurrent()
        return true
    }

    /** The canonical web URL for a thread, or null if the provider is unknown. */
    fun threadWebUrl(key: ThreadKey): String? {
        val meta = byId[key.provider]?.metadata ?: return null
        val base = meta.baseUrl.trimEnd('/')
        return when {
            meta.engine == EngineKind.FOURCHAN || key.provider.value == "fourchan" ->
                "$base/${key.board.value}/thread/${key.thread.value}"
            else -> "$base/${key.board.value}/res/${key.thread.value}.html"
        }
    }

    /** The provider's canonical board page, used by the catalog's Open in browser action. */
    fun boardWebUrl(board: SiteBoard): String? =
        byId[board.provider]
            ?.metadata
            ?.baseUrl
            ?.trimEnd('/')
            ?.let { "$it/${board.board.id.value}/" }

    fun retry() {
        Haptics.light()
        // First run's picker lists the boards, whatever tab sits under it.
        if (firstRun.state.value.needed) return loadBoards()
        when (_backStack.value.last()) {
            Route.Boards -> loadBoards()
            Route.Feed -> {
                loadBoards()
                loadFeed(force = true)
            }
            Route.Search -> search.run()
            else -> loadCurrent(force = true)
        }
    }

    private fun loadBoards() = directoryLoader.load()

    private fun loadCurrent(force: Boolean = false) {
        cancelHiddenPages()
        when (val route = _backStack.value.last()) {
            Route.Feed -> loadFeed(force)
            Route.Boards,
            Route.Downloads,
            Route.Saved,
            Route.History,
            Route.Watched,
            Route.Search,
            Route.Settings,
            -> Unit
            is Route.Catalog, is Route.BoardMedia -> {
                val board = route.catalogBoard ?: return
                loadCatalog(board, force)
            }
            is Route.ThreadPage ->
                if (force || threadShown != route.key || _thread.value !is Load.Ready) {
                    loadThread(route.key)
                }
            is Route.ThreadVideos ->
                if (force || threadShown != route.key || _thread.value !is Load.Ready) {
                    loadThread(route.key)
                }
            is Route.Media ->
                if (force || threadShown != route.thread || _thread.value !is Load.Ready) {
                    loadThread(route.thread)
                }
        }
    }

    /** Reloads the visible catalog even when a thread is open beside it. */
    fun refreshCatalog(board: SiteBoard) {
        if (visibleCatalogBoard() != board) return
        Haptics.light()
        loadCatalog(board, force = true)
    }

    /** A rendered reader reports the actual panes; stale disposal from another route is ignored. */
    internal fun setReaderPanesVisible(
        reader: BoardReaderRoute,
        wideWindow: Boolean,
        catalogVisible: Boolean,
        threadVisible: Boolean,
    ) {
        if (boardReaderRoute(_backStack.value) != reader) return
        readerLayoutWide = wideWindow
        if (!catalogVisible) cancelPage(catalogLoad, _catalogRefreshing)
        if (!threadVisible) cancelPage(threadLoad, _threadRefreshing)
        // A hidden initial load has no cache to restore. A canceled refresh keeps its Ready value.
        if (catalogVisible && _catalog.value is Load.Loading && catalogLoad.job?.isActive != true) {
            loadCatalog(reader.catalog.board)
        }
        if (threadVisible && _thread.value is Load.Loading && threadLoad.job?.isActive != true) {
            reader.thread?.let { loadThread(it.key) }
        }
    }

    private fun visibleCatalogBoard(): SiteBoard? =
        _backStack.value.last().catalogBoard
            ?: boardReaderRoute(_backStack.value)?.takeIf { readerLayoutWide }?.catalog?.board

    private fun cancelHiddenPages() {
        val route = _backStack.value.last()
        if (visibleCatalogBoard() == null) cancelPage(catalogLoad, _catalogRefreshing)
        val loadingDirectMedia = route is Route.Media && route.attachmentId != null
        if (route !is Route.ThreadPage && route !is Route.ThreadVideos && !loadingDirectMedia) {
            cancelPage(threadLoad, _threadRefreshing)
        }
        if (boardReaderRoute(_backStack.value) == null) readerLayoutWide = false
    }

    private fun cancelPage(
        page: PageLoad,
        refreshing: MutableStateFlow<Boolean>,
    ) {
        if (page.job?.isActive == true) {
            page.generation++
            page.job?.cancel()
            page.job = null
        }
        refreshing.value = false
    }

    private fun loadCatalog(
        board: SiteBoard,
        force: Boolean = false,
    ) {
        if (!force && catalogShown == board && _catalog.value is Load.Ready) return
        val sameBoard = catalogShown == board
        catalogShown = board
        if (!sameBoard) _preparedCatalogRows.value = emptyMap()
        pageLoader.load(
            catalogLoad,
            _catalog,
            _catalogRefreshing,
            _catalogRefreshFailure,
            retainContent = sameBoard,
            preparePublication = { threads ->
                val timestamp = now()
                val rows = withContext(readerDispatcher) { threads.associate { it.key to it.toRow(timestamp) } }
                val publish: () -> Unit = {
                    preparedCatalogSource.value = threads
                    val visible = (catalog.value as? Load.Ready)?.value.orEmpty()
                    _preparedCatalogRows.value = retainVisibleCatalogRows(visible, _preparedCatalogRows.value, rows)
                }
                publish
            },
        ) { provider(board.provider).catalogOf(board.board.id, readerDispatcher) }
    }

    private fun loadThread(key: ThreadKey) {
        val sameThread = threadShown == key
        threadShown = key
        if (!sameThread) {
            _preparedThread.value = null
            _firstUnreadPostId.value = null
        }
        pageLoader.load(
            threadLoad,
            _thread,
            _threadRefreshing,
            _threadRefreshFailure,
            retainContent = sameThread,
            preparePublication = { loaded ->
                val index = loaded.preparePresentation(nowMillis = now(), dispatcher = readerDispatcher)
                val firstUnread = watched.read(loaded)
                val publish: () -> Unit = {
                    _preparedThread.value = index
                    _firstUnreadPostId.value = firstUnread
                }
                publish
            },
        ) { threadOf(key) }
    }

    /** Home owns its source/mode cache and publishes each bounded discovery batch incrementally. */
    private fun loadFeed(force: Boolean = false) = homeFeed.load(force)

    /**
     * The followed boards a feed or search covers: [boards], less those the permanent filter
     * catches by title and, when Settings hides them, NSFW ones, as on Android. What a board is
     * comes from the boards list once it has loaded.
     */
    private fun searchableBoards(boards: Set<FollowedBoard> = followed.value): List<FollowedBoard> =
        filterSearchableBoards(
            boards,
            (_boards.value as? Load.Ready)?.value.orEmpty(),
            settings.current.value.nsfwContentHidden,
        )

    private val threadReader by lazy {
        BrowserThreadReader(
            settings = { this@Browser.settings.current.value },
            board = { key ->
                (_boards.value as? Load.Ready)
                    ?.value
                    ?.firstOrNull { it.provider == key.provider && it.board.id == key.board }
                    ?.board
            },
            fetch = { key -> provider(key.provider).getThread(key.board, key.thread) },
            copies = threadCopies,
            history = history,
            dispatcher = readerDispatcher,
            now = now,
        )
    }

    private suspend fun threadOf(key: ThreadKey): Thread = threadReader.load(key)

    private fun provider(id: ProviderId): ImageBoardProvider =
        byId[id] ?: throw ProviderException.NotFound("No site ${id.value}")
}

/** A one-line reason for the error view. Provider failures already carry a readable message. */
internal fun Throwable.readable(): String = message?.takeIf { it.isNotBlank() } ?: (this::class.simpleName ?: "Error")

internal fun Throwable.browserFailureCause(): BrowserFailureCause =
    when (this) {
        is ProviderException.Network -> BrowserFailureCause.NETWORK
        is ProviderException.NotFound -> BrowserFailureCause.NOT_FOUND
        is ProviderException.Http ->
            if (code == HttpStatusCode.NotFound.value) BrowserFailureCause.NOT_FOUND else BrowserFailureCause.SERVER
        is ProviderException.RateLimited -> BrowserFailureCause.SERVER
        else -> BrowserFailureCause.UNKNOWN
    }

/** A site the app reads: its provider id and the name it goes by. */
data class Site(
    val id: ProviderId,
    val name: String,
)

/** A board someone follows: its site and its id. */
data class FollowedBoard(
    val provider: ProviderId,
    val board: BoardId,
)

/**
 * [load] over every board, at most [MAX_CONCURRENT_BOARD_LOADS] at a time, each board's outcome
 * kept apart so one failing leaves the others.
 */
internal suspend fun <T> loadEach(
    boards: List<FollowedBoard>,
    load: suspend (FollowedBoard) -> T,
): List<Result<T>> {
    val gate = Semaphore(MAX_CONCURRENT_BOARD_LOADS)
    return coroutineScope {
        boards.map { board -> async { gate.withPermit { runCatching { load(board) } } } }.awaitAll()
    }
}

/** Android loads at most this many followed catalogs at once; so does iOS, for the feed and search. */
private const val MAX_CONCURRENT_BOARD_LOADS = 4

// Filtering before adapting keeps both providers consistent with the Android directory.
internal suspend fun ImageBoardProvider.siteBoards(): List<SiteBoard> =
    ContentPolicy
        .visibleBoards(
            getBoards().ifEmpty {
                error("no boards returned")
            },
        ).map { SiteBoard(metadata.id, metadata.displayName, it) }

private suspend fun ImageBoardProvider.catalogOf(
    board: BoardId,
    dispatcher: CoroutineDispatcher,
): List<CatalogThread> {
    val loaded = getCatalog(CatalogRequest(metadata.id, board))
    return withContext(dispatcher) { ContentPolicy.visibleCatalog(loaded) }
}
