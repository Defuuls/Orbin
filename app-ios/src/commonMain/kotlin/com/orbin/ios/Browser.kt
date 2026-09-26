package com.orbin.ios

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.FeedSort
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.comparator
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.core.model.matchesFilterTokens
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock

/** Something being fetched: still coming, arrived, or failed with a message to show. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>

    data class Ready<T>(
        val value: T,
    ) : Load<T>

    data class Failed(
        val message: String,
    ) : Load<Nothing>
}

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

/** Where the reader is. The back stack is a list of these, a tab (feed or boards) at the bottom. */
sealed interface Route {
    /** The newest threads of every followed board, one list: the start screen, as on Android. */
    data object Feed : Route

    data object Boards : Route

    /** Search over the followed boards, opened from the boards list. */
    data object Search : Route

    /** Settings, opened from the feed's header or the boards list. */
    data object Settings : Route

    data class Catalog(
        val board: SiteBoard,
    ) : Route

    data class ThreadPage(
        val key: ThreadKey,
    ) : Route

    /** The full-screen viewer over the files of the open thread, starting at file [index]. */
    data class Media(
        val thread: ThreadKey,
        val index: Int,
    ) : Route
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
 */
class Browser(
    private val providers: List<ImageBoardProvider>,
    private val bookmarks: BookmarkRepository,
    private val history: HistoryRepository,
    private val boardPreferences: BoardPreferencesRepository,
    settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val byId = providers.associateBy { it.metadata.id }

    /** The reader's settings; hiding NSFW boards shapes the boards list, the feed and search. */
    val settings = ReaderSettings(settings, history, scope)

    /** Searching the followed boards' catalogs. */
    val search =
        ThreadSearch(scope, boards = ::searchableBoards) { board ->
            provider(board.provider).getCatalog(CatalogRequest(board.provider, board.board))
        }

    /** Watching threads, their unread counts, and keeping those counts current. */
    val watched =
        WatchedThreads(bookmarks, scope, now) { key -> provider(key.provider).getThread(key.board, key.thread) }

    private val _backStack = MutableStateFlow<List<Route>>(listOf(Route.Feed))
    val backStack: StateFlow<List<Route>> = _backStack.asStateFlow()

    private val _boards = MutableStateFlow<Load<List<SiteBoard>>>(Load.Loading)
    val boards: StateFlow<Load<List<SiteBoard>>> = _boards.asStateFlow()

    private val _catalog = MutableStateFlow<Load<List<CatalogThread>>>(Load.Loading)
    val catalog: StateFlow<Load<List<CatalogThread>>> = _catalog.asStateFlow()

    private val _thread = MutableStateFlow<Load<Thread>>(Load.Loading)
    val thread: StateFlow<Load<Thread>> = _thread.asStateFlow()

    private val _feed = MutableStateFlow<Load<List<FeedThread>>>(Load.Loading)
    val feed: StateFlow<Load<List<FeedThread>>> = _feed.asStateFlow()

    private val _firstUnreadPostId = MutableStateFlow<String?>(null)

    /**
     * The first reply the reader had not seen when the open thread loaded, if it is watched and has
     * new replies: where the thread screen's "jump to unread" goes, as on Android.
     */
    val firstUnreadPostId: StateFlow<String?> = _firstUnreadPostId.asStateFlow()

    /** Every followed board, on every site: what the feed is made of and the boards list marks. */
    val followed: StateFlow<Set<FollowedBoard>> =
        combine(
            providers.map { provider ->
                val id = provider.metadata.id
                boardPreferences.observeSubscribedBoards(id).map { boards -> boards.map { FollowedBoard(id, it) } }
            },
        ) { perSite -> perSite.flatMap { it }.toSet() }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    // What the feed holds: the followed boards, and whether NSFW ones were hidden.
    private var feedFor: Pair<Set<FollowedBoard>, Boolean>? = null
    private var feedLoad: Job? = null

    private var pageLoad: Job? = null

    // What the catalog and thread states hold, so returning to them does not fetch them again.
    private var catalogShown: SiteBoard? = null
    private var threadShown: ThreadKey? = null

    init {
        loadBoards()
        // The feed follows the followed set: follow a board and its threads join the list.
        scope.launch {
            followed.drop(1).collect { if (_backStack.value.last() == Route.Feed) loadFeed() }
        }
        scope.launch {
            this@Browser
                .settings.current
                .map { it.hideNsfwBoards }
                .distinctUntilChanged()
                .drop(1)
                .collect { if (_backStack.value.last() == Route.Feed) loadFeed() }
        }
        loadFeed()
        watched.refresh()
    }

    /** Switches to a tab, leaving whatever was open on the other one. */
    fun openTab(tab: Route) {
        require(tab == Route.Feed || tab == Route.Boards) { "Not a tab: $tab" }
        _backStack.value = listOf(tab)
        if (tab == Route.Feed) loadFeed()
        watched.refresh()
    }

    /** Follows or unfollows [board], as the boards list's switch does on Android. */
    fun setFollowed(
        board: SiteBoard,
        follow: Boolean,
    ) {
        scope.launch { boardPreferences.setSubscribedBoard(board.provider, board.board.id, follow) }
    }

    /** Every thread the reader has opened, on any board, for the feed's read state. */
    fun visitedKeys(): Flow<Set<ThreadKey>> = history.observeVisitedKeys()

    /** Opens a page that loads nothing of its own: search or settings. */
    fun open(page: Route) {
        require(page == Route.Search || page == Route.Settings) { "Not a page: $page" }
        _backStack.update { it + page }
    }

    fun openBoard(board: SiteBoard) {
        _backStack.update { it + Route.Catalog(board) }
        loadCurrent()
    }

    fun openThread(key: ThreadKey) {
        _backStack.update { it + Route.ThreadPage(key) }
        loadCurrent()
    }

    /** Thread numbers on [board] the reader has opened, for the catalog's read state. */
    fun visitedThreads(board: SiteBoard): Flow<Set<Long>> =
        history.observeVisitedThreadIds(board.provider, board.board.id)

    /** Opens the viewer on the open thread's file number [index], counted across all its posts. */
    fun openMedia(index: Int) {
        val thread = (_backStack.value.last() as? Route.ThreadPage)?.key ?: return
        _backStack.update { it + Route.Media(thread, index) }
    }

    /** Pops one destination. Returns false at the boards list, where there is nothing to pop. */
    fun back(): Boolean {
        if (_backStack.value.size <= 1) return false
        _backStack.update { it.dropLast(1) }
        loadCurrent()
        return true
    }

    fun retry() {
        when (_backStack.value.last()) {
            Route.Boards -> loadBoards()
            Route.Feed -> loadFeed(force = true)
            Route.Search -> search.run()
            else -> loadCurrent(force = true)
        }
    }

    private fun loadBoards() {
        _boards.value = Load.Loading
        scope.launch {
            // One site being down should not hide the others' boards.
            val perSite =
                providers
                    .map { provider -> async { runCatching { provider.siteBoards() } } }
                    .awaitAll()
            val loaded = perSite.mapNotNull { it.getOrNull() }.flatten()
            _boards.value =
                if (loaded.isEmpty() && perSite.any { it.isFailure }) {
                    Load.Failed(perSite.firstNotNullOf { it.exceptionOrNull() }.readable())
                } else {
                    Load.Ready(loaded)
                }
        }
    }

    private fun loadCurrent(force: Boolean = false) {
        when (val route = _backStack.value.last()) {
            Route.Feed, Route.Boards, Route.Search, Route.Settings, is Route.Media -> Unit
            is Route.Catalog ->
                if (force || catalogShown != route.board || _catalog.value !is Load.Ready) {
                    catalogShown = route.board
                    load(_catalog) { catalogOf(route.board) }
                }
            is Route.ThreadPage ->
                if (force || threadShown != route.key || _thread.value !is Load.Ready) {
                    threadShown = route.key
                    load(_thread) { threadOf(route.key) }
                }
        }
    }

    private fun <T> load(
        target: MutableStateFlow<Load<T>>,
        fetch: suspend () -> T,
    ) {
        pageLoad?.cancel()
        target.value = Load.Loading
        pageLoad =
            scope.launch {
                target.value =
                    try {
                        Load.Ready(fetch())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (
                        @Suppress("TooGenericExceptionCaught") error: Exception,
                    ) {
                        Load.Failed(error.readable())
                    }
            }
    }

    /**
     * Loads the feed unless it already holds the current followed set, which is how coming back to
     * the feed tab shows it as it was, as Android's feed does.
     */
    private fun loadFeed(force: Boolean = false) {
        val boards = followed.value
        val shown = boards to settings.current.value.hideNsfwBoards
        if (!force && shown == feedFor && _feed.value is Load.Ready) return
        feedFor = shown
        feedLoad?.cancel()
        _feed.value = Load.Loading
        feedLoad =
            scope.launch {
                _feed.value =
                    try {
                        feedOf(boards)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (
                        @Suppress("TooGenericExceptionCaught") error: Exception,
                    ) {
                        Load.Failed(error.readable())
                    }
            }
    }

    /**
     * Android's feed, board by board: each followed board's catalog, at most [MAX_CONCURRENT_BOARD_LOADS]
     * at a time, cut to the board's thread limit and passed through the permanent filter. A board
     * that fails to load leaves the others; only when every one fails is the feed an error.
     */
    private suspend fun feedOf(boards: Set<FollowedBoard>): Load<List<FeedThread>> {
        val wanted = searchableBoards(boards)
        if (wanted.isEmpty()) return Load.Ready(emptyList())
        val limits =
            wanted.groupBy { it.provider }.mapValues { (provider, onSite) ->
                boardPreferences.observeFeedThreadLimits(provider, onSite.map { it.board }.toSet()).first()
            }
        val results =
            loadEach(wanted) { board ->
                val catalog = provider(board.provider).getCatalog(CatalogRequest(board.provider, board.board))
                val limit = limits[board.provider]?.get(board.board)?.count
                (limit?.let(catalog::take) ?: catalog)
                    .filterNot { it.matchesFilterTokens(emptySet()) }
                    .map { FeedThread(board.provider, it) }
            }
        val loaded = results.mapNotNull { it.getOrNull() }
        return if (loaded.isEmpty()) {
            Load.Failed(results.firstNotNullOf { it.exceptionOrNull() }.readable())
        } else {
            Load.Ready(loaded.flatten().sortedWith(compareBy(FeedSort.BOARD.comparator()) { it.thread }))
        }
    }

    /**
     * The followed boards a feed or search covers: [boards], less those the permanent filter
     * catches by title and, when Settings hides them, NSFW ones, as on Android. What a board is
     * comes from the boards list once it has loaded.
     */
    private fun searchableBoards(boards: Set<FollowedBoard> = followed.value): List<FollowedBoard> {
        val boardInfo =
            (_boards.value as? Load.Ready)?.value.orEmpty().associateBy { FollowedBoard(it.provider, it.board.id) }
        val hideNsfw = settings.current.value.hideNsfwBoards
        return boards.filterNot { followedBoard ->
            val board = boardInfo[followedBoard]?.board
            board != null && (board.isPermanentlyFiltered() || (hideNsfw && board.isNsfw))
        }
    }

    private suspend fun ImageBoardProvider.siteBoards(): List<SiteBoard> =
        getBoards().map { SiteBoard(metadata.id, metadata.displayName, it) }

    private suspend fun catalogOf(board: SiteBoard): List<CatalogThread> =
        provider(board.provider).getCatalog(CatalogRequest(board.provider, board.board.id))

    private suspend fun threadOf(key: ThreadKey): Thread {
        _firstUnreadPostId.value = null
        return provider(key.provider).getThread(key.board, key.thread).also {
            recordVisit(it)
            _firstUnreadPostId.value = watched.read(it)
        }
    }

    // Android records a visit when a thread loads and skips the permanently filtered ones; so does
    // this. A failed write only loses the read mark, so it never fails the load.
    private suspend fun recordVisit(thread: Thread) {
        if (thread.isPermanentlyFiltered()) return
        runCatching { history.record(thread.toHistoryEntry(now())) }
    }

    private fun provider(id: ProviderId): ImageBoardProvider =
        byId[id] ?: throw ProviderException.NotFound("No site ${id.value}")
}

/** A one-line reason for the error view. Provider failures already carry a readable message. */
internal fun Throwable.readable(): String = message?.takeIf { it.isNotBlank() } ?: (this::class.simpleName ?: "Error")

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
