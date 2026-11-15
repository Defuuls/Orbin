package com.orbin.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.PermanentContentFilter
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadAvailability
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.isPermanentlyFiltered
import com.orbin.core.ui.date.formatPostDateTime
import com.orbin.domain.bookmarks.BookmarkStatusRefresher
import com.orbin.domain.bookmarks.collectionUnread
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.repository.ThreadMemoryRepository
import com.orbin.provider.api.ProviderRegistry
import com.orbin.uinext.NextDestination
import com.orbin.uinext.ThreadCollectionRow
import com.orbin.uinext.ThreadCollectionScreen
import com.orbin.uinext.ThreadCollectionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

internal enum class CollectionKind { SAVED, HISTORY, WATCHED }

internal data class CollectionEntry(
    val key: ThreadKey,
    val row: ThreadCollectionRow,
)

@HiltViewModel
internal class ThreadCollectionsViewModel
    @Inject
    constructor(
        private val bookmarks: BookmarkRepository,
        private val history: HistoryRepository,
        private val settings: SettingsRepository,
        private val memory: ThreadMemoryRepository,
        private val statuses: BookmarkStatusRefresher,
        boards: BoardRepository,
        providers: ProviderRegistry,
    ) : ViewModel() {
        private val directory: Flow<Map<Pair<ProviderId, BoardId>, Board>> =
            providers
                .all()
                .map { provider ->
                    boards.observeBoards(provider.metadata.id).map { boards ->
                        boards.associateBy { provider.metadata.id to it.id }
                    }
                }.let { sources ->
                    if (sources.isEmpty()) {
                        flowOf(emptyMap())
                    } else {
                        combine(sources) {
                            it.fold(emptyMap()) { all, part ->
                                all +
                                    part
                            }
                        }
                    }
                }
        val refreshing = statuses.isRefreshing
        val newestFirst =
            settings.settings
                .map { it.collectionNewestFirst }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
        val collections =
            combine(
                bookmarks.observeBookmarks(),
                history.observeHistory(),
                settings.settings,
                memory.observeCurrent(),
                directory,
            ) { allSaved, allVisited, prefs, readState, directory ->
                fun allowed(
                    key: ThreadKey,
                    title: String,
                ): Boolean {
                    val board = directory[key.provider to key.board]
                    return !PermanentContentFilter.matchesAny(listOf(key.board.value, title)) &&
                        board?.isPermanentlyFiltered() != true &&
                        (!prefs.nsfwContentHidden || board?.isNsfw == false)
                }
                val saved = allSaved.filter { allowed(it.key, it.title) }
                val visited = allVisited.filter { allowed(it.key, it.title) }
                val memoryByKey = readState.associateBy { it.key }
                val bookmarkRows =
                    saved
                        .sortedBy { if (prefs.collectionNewestFirst) -it.createdAtMillis else it.createdAtMillis }
                        .map { bookmark ->
                            CollectionEntry(
                                bookmark.key,
                                ThreadCollectionRow(
                                    id = bookmark.key.toString(),
                                    title = bookmark.title,
                                    source = bookmark.key.collectionSource(),
                                    time = formatPostDateTime(bookmark.createdAtMillis).orEmpty(),
                                    threadNumber =
                                        bookmark.key.thread.value
                                            .toString(),
                                    unread = bookmark.collectionUnread(memoryByKey[bookmark.key]),
                                    availability = ThreadCollectionStatus.valueOf(bookmark.availability.name),
                                ),
                            )
                        }
                val watched = saved.filter { it.isWatched }.mapTo(mutableSetOf()) { it.key }
                mapOf(
                    CollectionKind.SAVED to bookmarkRows,
                    CollectionKind.WATCHED to bookmarkRows.filter { it.key in watched },
                    CollectionKind.HISTORY to
                        visited
                            .sortedBy {
                                if (prefs.collectionNewestFirst) -it.lastVisitedMillis else it.lastVisitedMillis
                            }.map { entry ->
                                CollectionEntry(
                                    entry.key,
                                    ThreadCollectionRow(
                                        id = entry.key.toString(),
                                        title = entry.title,
                                        source = entry.key.collectionSource(),
                                        time = formatPostDateTime(entry.lastVisitedMillis).orEmpty(),
                                        threadNumber =
                                            entry.key.thread.value
                                                .toString(),
                                        unread =
                                            saved.firstOrNull { it.key == entry.key }?.collectionUnread(
                                                memoryByKey[entry.key],
                                            )
                                                ?: memoryByKey[entry.key]?.collectionUnread()
                                                ?: 0,
                                    ),
                                )
                            },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

        fun refresh(force: Boolean = false) {
            viewModelScope.launch { statuses.refreshAll(force) }
        }

        fun toggleOrder() {
            viewModelScope.launch {
                settings.setCollectionNewestFirst(
                    !settings.settings.first().collectionNewestFirst,
                )
            }
        }

        fun remove(
            kind: CollectionKind,
            key: ThreadKey,
        ) {
            viewModelScope.launch {
                if (kind == CollectionKind.HISTORY) history.remove(key) else bookmarks.removeBookmark(key)
            }
        }

        fun clear(kind: CollectionKind) {
            viewModelScope.launch {
                if (kind ==
                    CollectionKind.HISTORY
                ) {
                    history.clear()
                } else {
                    selected(kind).forEach { bookmarks.removeBookmark(it.key) }
                }
            }
        }

        fun removeDeleted(kind: CollectionKind) {
            viewModelScope.launch {
                selected(
                    kind,
                ).filter { it.availability == ThreadAvailability.DELETED }.forEach { bookmarks.removeBookmark(it.key) }
            }
        }

        fun markRead(key: ThreadKey) {
            viewModelScope.launch { markReadNow(key) }
        }

        fun markAllRead(kind: CollectionKind) {
            viewModelScope.launch { selected(kind).forEach { markReadNow(it.key) } }
        }

        private suspend fun selected(kind: CollectionKind) =
            bookmarks
                .observeBookmarks()
                .first()
                .filter { kind != CollectionKind.WATCHED || it.isWatched }

        private suspend fun markReadNow(key: ThreadKey) {
            val latest = bookmarks.getBookmark(key)?.latestReplyCount ?: 0
            memory.update(
                key,
            ) { it.copy(seenPostIds = it.seenPostIds + it.knownPostIds, acknowledgedReplyCount = latest) }
            if (!settings.settings.first().historySessionOnly) bookmarks.markRead(key)
        }
    }

@Composable
internal fun ThreadCollectionsDestination(
    kind: CollectionKind,
    title: String,
    onOpenThread: (String, String, Long, String) -> Unit,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onDestination: ((NextDestination) -> Unit)? = null,
    viewModel: ThreadCollectionsViewModel = hiltViewModel(),
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val newestFirst by viewModel.newestFirst.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    LaunchedEffect(kind) { if (kind != CollectionKind.HISTORY) viewModel.refresh() }
    val entries = collections[kind].orEmpty()
    val byId = entries.associateBy { it.row.id }
    val bookmarkCollection = kind != CollectionKind.HISTORY
    ThreadCollectionScreen(
        title = title,
        rows = entries.map { it.row },
        onOpen = { row ->
            byId[row.id]?.let {
                onOpenThread(
                    it.key.provider.value,
                    it.key.board.value,
                    it.key.thread.value,
                    it.row.title,
                )
            }
        },
        onBack = onBack,
        onSettings = onSettings,
        onDestination = onDestination,
        selectedDestination = if (kind == CollectionKind.WATCHED) NextDestination.WATCHED else NextDestination.SAVED,
        newestFirst = newestFirst,
        refreshing = refreshing,
        onRefresh = { viewModel.refresh(true) }.takeIf { bookmarkCollection },
        onToggleOrder = viewModel::toggleOrder,
        onRemove = { row -> byId[row.id]?.let { viewModel.remove(kind, it.key) } },
        onMarkRead =
            { row: ThreadCollectionRow ->
                byId[row.id]?.let { viewModel.markRead(it.key) }
                Unit
            }.takeIf { bookmarkCollection },
        onMarkAllRead = { viewModel.markAllRead(kind) }.takeIf { bookmarkCollection },
        onClear = { viewModel.clear(kind) },
        onRemoveDeleted = { viewModel.removeDeleted(kind) }.takeIf { bookmarkCollection },
    )
}

private fun ThreadKey.collectionSource(): String {
    val site =
        when (provider.value) {
            "fourchan" -> "4chan"
            "bbwchan" -> "BBWChan"
            else -> provider.value
        }
    return "$site · /${board.value}/"
}
