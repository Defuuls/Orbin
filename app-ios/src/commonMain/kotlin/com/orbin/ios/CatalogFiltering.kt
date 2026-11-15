package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.UserContentFilters
import com.orbin.uinext.FeedRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A setting change refilters the retained source snapshot without making another request. */
internal fun filterReaderCatalog(
    source: Flow<Load<List<CatalogThread>>>,
    settings: Flow<AppSettings>,
    dispatcher: CoroutineDispatcher,
): Flow<Load<List<CatalogThread>>> =
    combine(source, settings.map { it.hiddenThreadPatterns to it.showStickyThreads }.distinctUntilChanged()) {
        loaded,
        (patterns, showSticky),
        ->
        if (loaded !is Load.Ready || patterns.isEmpty() && showSticky) {
            loaded
        } else {
            withContext(dispatcher) {
                val filters = UserContentFilters(threadPatterns = patterns)
                Load.Ready(
                    loaded.value.filter { thread ->
                        (showSticky || !thread.stats.isSticky) && (patterns.isEmpty() || !filters.hidesThread(thread))
                    },
                )
            }
        }
    }

/** Keep the currently published rows until its asynchronous filtered replacement is published. */
internal fun retainVisibleCatalogRows(
    visible: List<CatalogThread>,
    previous: Map<ThreadKey, FeedRow>,
    fresh: Map<ThreadKey, FeedRow>,
): Map<ThreadKey, FeedRow> {
    val visibleKeys = visible.mapTo(mutableSetOf()) { it.key }
    return previous.filterKeys { it in visibleKeys } + fresh
}

/** Trim an old publication only after the replacement can display every currently visible row. */
internal fun observeCatalogRowRetention(
    scope: CoroutineScope,
    source: StateFlow<Load<List<CatalogThread>>>,
    visible: StateFlow<Load<List<CatalogThread>>>,
    preparedSource: StateFlow<List<CatalogThread>?>,
    rows: MutableStateFlow<Map<ThreadKey, FeedRow>>,
) {
    scope.launch {
        visible.collect { publication ->
            val published = (publication as? Load.Ready)?.value ?: return@collect
            val raw = (source.value as? Load.Ready)?.value ?: return@collect
            if (raw != preparedSource.value) return@collect
            val sourceKeys = raw.mapTo(mutableSetOf()) { it.key }
            if (published.any { it.key !in sourceKeys }) return@collect
            rows.update { current ->
                val sameSource = (source.value as? Load.Ready)?.value == raw && preparedSource.value == raw
                if (!sameSource || current.keys == sourceKeys) current else current.filterKeys { it in sourceKeys }
            }
        }
    }
}
