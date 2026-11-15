package com.orbin.ios

import com.orbin.core.model.ProviderId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** Automatic sync observes portable data only. Raw reading-session state is deliberately absent. */
@OptIn(FlowPreview::class)
internal fun observeCloudChanges(
    sync: CloudSyncController,
    scope: CoroutineScope,
    repositories: CloudRepositories,
    providers: List<ProviderId>,
) = scope.launch {
    val changes =
        buildList<Flow<Unit>> {
            add(repositories.settings.settings.map { })
            add(repositories.bookmarks.observeBookmarks().map { })
            add(repositories.history.observeAllEntries().map { })
            add(repositories.memory.observeAll().map { })
            add(repositories.media.observeAll().map { })
            for (provider in providers) {
                add(repositories.boardPreferences.observeSubscribedBoards(provider).map { })
                add(repositories.boardPreferences.observeFavoriteBoards(provider).map { })
            }
        }
    changes.merge().debounce(CLOUD_CHANGE_DEBOUNCE_MILLIS).collect { sync.localDataChanged() }
}

private const val CLOUD_CHANGE_DEBOUNCE_MILLIS = 2_000L
