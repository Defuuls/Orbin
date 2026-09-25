package com.orbin.ios

import com.orbin.core.model.Board
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.ProviderId
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadKey
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

/** Where the reader is. The back stack is a list of these, boards at the bottom. */
sealed interface Route {
    data object Boards : Route

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
 * Each destination loads when it is opened and again on [retry]. Going back to a page that already
 * loaded shows it as it was rather than fetching it again. Starting a new load cancels the one
 * still running, so a slow thread cannot overwrite the one the reader opened next.
 */
class Browser(
    private val providers: List<ImageBoardProvider>,
    private val scope: CoroutineScope,
) {
    private val byId = providers.associateBy { it.metadata.id }

    private val _backStack = MutableStateFlow<List<Route>>(listOf(Route.Boards))
    val backStack: StateFlow<List<Route>> = _backStack.asStateFlow()

    private val _boards = MutableStateFlow<Load<List<SiteBoard>>>(Load.Loading)
    val boards: StateFlow<Load<List<SiteBoard>>> = _boards.asStateFlow()

    private val _catalog = MutableStateFlow<Load<List<CatalogThread>>>(Load.Loading)
    val catalog: StateFlow<Load<List<CatalogThread>>> = _catalog.asStateFlow()

    private val _thread = MutableStateFlow<Load<Thread>>(Load.Loading)
    val thread: StateFlow<Load<Thread>> = _thread.asStateFlow()

    private var pageLoad: Job? = null

    // What the catalog and thread states hold, so returning to them does not fetch them again.
    private var catalogShown: SiteBoard? = null
    private var threadShown: ThreadKey? = null

    init {
        loadBoards()
    }

    fun openBoard(board: SiteBoard) {
        _backStack.update { it + Route.Catalog(board) }
        loadCurrent()
    }

    fun openThread(key: ThreadKey) {
        _backStack.update { it + Route.ThreadPage(key) }
        loadCurrent()
    }

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
        if (_backStack.value.last() == Route.Boards) loadBoards() else loadCurrent(force = true)
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
            Route.Boards, is Route.Media -> Unit
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
        pageLoad = scope.launch {
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

    private suspend fun ImageBoardProvider.siteBoards(): List<SiteBoard> =
        getBoards().map { SiteBoard(metadata.id, metadata.displayName, it) }

    private suspend fun catalogOf(board: SiteBoard): List<CatalogThread> =
        provider(board.provider).getCatalog(CatalogRequest(board.provider, board.board.id))

    private suspend fun threadOf(key: ThreadKey): Thread = provider(key.provider).getThread(key.board, key.thread)

    private fun provider(id: ProviderId): ImageBoardProvider =
        byId[id] ?: throw ProviderException.NotFound("No site ${id.value}")
}

/** A one-line reason for the error view. Provider failures already carry a readable message. */
internal fun Throwable.readable(): String = message?.takeIf { it.isNotBlank() } ?: (this::class.simpleName ?: "Error")
