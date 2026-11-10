package com.orbin.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.orbin.core.model.ThreadKey
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.uinext.NextDestination
import com.orbin.uinext.ThreadCollectionRow
import com.orbin.uinext.ThreadCollectionScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

internal enum class CollectionKind { SAVED, HISTORY, WATCHED }

internal data class CollectionEntry(
    val key: ThreadKey,
    val title: String,
    val detail: String = "",
) {
    val row: ThreadCollectionRow
        get() =
            ThreadCollectionRow(
                id = "${key.provider.value}/${key.board.value}/${key.thread.value}",
                title = title,
                source = "${siteName(key.provider.value)} · /${key.board.value}/",
                detail = detail,
            )
}

@HiltViewModel
internal class ThreadCollectionsViewModel
    @Inject
    constructor(
        bookmarks: BookmarkRepository,
        history: HistoryRepository,
    ) : ViewModel() {
        val collections =
            combine(bookmarks.observeBookmarks(), history.observeHistory()) { saved, visited ->
                mapOf(
                    CollectionKind.SAVED to
                        saved.sortedByDescending { it.createdAtMillis }.map { CollectionEntry(it.key, it.title) },
                    CollectionKind.WATCHED to
                        saved
                            .filter { it.isWatched }
                            .sortedByDescending { it.createdAtMillis }
                            .map { CollectionEntry(it.key, it.title) },
                    CollectionKind.HISTORY to
                        visited
                            .sortedByDescending { it.lastVisitedMillis }
                            .map { CollectionEntry(it.key, it.title) },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
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
    val entries = collections[kind].orEmpty()
    val byId = entries.associateBy { it.row.id }
    ThreadCollectionScreen(
        title = title,
        rows = entries.map { it.row },
        onOpen = { row ->
            byId[row.id]?.let { onOpenThread(it.key.provider.value, it.key.board.value, it.key.thread.value, it.title) }
        },
        onBack = onBack,
        onSettings = onSettings,
        onDestination = onDestination,
        selectedDestination = if (kind == CollectionKind.WATCHED) NextDestination.WATCHED else NextDestination.SAVED,
    )
}

private fun siteName(provider: String): String =
    when (provider) {
        "fourchan" -> "4chan"
        "bbwchan" -> "BBWChan"
        else -> provider
    }
