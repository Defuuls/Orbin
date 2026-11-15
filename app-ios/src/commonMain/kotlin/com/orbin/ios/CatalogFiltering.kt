package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.UserContentFilters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
