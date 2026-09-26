package com.orbin.ios

import com.orbin.core.model.CatalogThread
import com.orbin.core.model.FeedSort
import com.orbin.core.model.SearchQuery
import com.orbin.core.model.SearchScope
import com.orbin.core.model.comparator
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.core.model.matchesSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android's search on iOS: one query over the catalogs of every board the reader follows, on
 * every site, matched by the same rule (`matchesSearch` in `:core:model`) after the permanent
 * filter. A board that fails to load leaves the others; only when every one fails is it an error.
 *
 * The query and the last results stay here, so opening a result and coming back finds them as
 * they were.
 */
class ThreadSearch(
    private val scope: CoroutineScope,
    private val boards: () -> List<FollowedBoard>,
    private val catalogOf: suspend (FollowedBoard) -> List<CatalogThread>,
) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<Load<List<FeedThread>>?>(null)

    /** The last search's results, or null before the first one. */
    val results: StateFlow<Load<List<FeedThread>>?> = _results.asStateFlow()

    private var running: Job? = null

    fun setQuery(text: String) {
        _query.value = text
    }

    /** Searches for the current query; a blank one does nothing, as on Android. */
    fun run(): Job? {
        val text = _query.value.trim()
        if (text.isEmpty()) return null
        running?.cancel()
        _results.value = Load.Loading
        return scope
            .launch {
                _results.value =
                    try {
                        searchFor(text)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (
                        @Suppress("TooGenericExceptionCaught") error: Exception,
                    ) {
                        Load.Failed(error.readable())
                    }
            }.also { running = it }
    }

    private suspend fun searchFor(text: String): Load<List<FeedThread>> {
        val wanted = boards()
        if (wanted.isEmpty()) return Load.Ready(emptyList())
        val perBoard =
            loadEach(wanted) { board ->
                val query = SearchQuery(board.provider, text, SearchScope.BOARD_CATALOG, board.board)
                catalogOf(board)
                    .filterNot { it.isPermanentlyFiltered() }
                    .filter { it.matchesSearch(query) }
                    .map { FeedThread(board.provider, it) }
            }
        val loaded = perBoard.mapNotNull { it.getOrNull() }
        return if (loaded.isEmpty()) {
            Load.Failed(perBoard.firstNotNullOf { it.exceptionOrNull() }.readable())
        } else {
            Load.Ready(loaded.flatten().sortedWith(compareBy(FeedSort.BOARD.comparator()) { it.thread }))
        }
    }
}
