package com.orbin.uinext.screens

import androidx.compose.runtime.Immutable
import com.orbin.uinext.components.FeedRow
import com.orbin.uinext.components.ReaderNavigation

/**
 * Everything Home shows, as plain values. Hosts build one per frame from their own state; the
 * screen never reaches back into the host. See [FeedScreen].
 *
 * [repliesOpenComments] says whether "View replies" opens comments; `null` means it does exactly
 * when the host offers no separate replies action.
 */
@Immutable
data class FeedScreenState(
    val rows: List<FeedRow>,
    val subtitle: String? = null,
    val statusMessage: String? = null,
    val showRail: Boolean = true,
    val query: String = "",
    val sourceLabels: List<String> = emptyList(),
    val selectedSource: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreKey: Any? = null,
    val mode: CatalogMode = CatalogMode.LIST,
    val wideWindow: Boolean = false,
    val downloadBadge: Int = 0,
    val watchedBadge: Int = 0,
    val emptyTitle: String? = null,
    val emptyMessage: String? = null,
    val repliesOpenComments: Boolean? = null,
)

/**
 * What Home can ask its host to do. A `null` action hides the control that would trigger it, so
 * a host offers a destination or row action simply by supplying it. Home is read-only: there is
 * deliberately no reply, compose or posting action.
 */
@Immutable
data class FeedScreenActions(
    val onOpenRow: (FeedRow) -> Unit = {},
    val onOpenMedia: ((FeedRow) -> Unit)? = null,
    val onOpenReplies: ((FeedRow) -> Unit)? = null,
    val onOpenBoard: ((FeedRow) -> Unit)? = null,
    val onToggleWatch: ((FeedRow) -> Unit)? = null,
    val onToggleSaved: ((FeedRow) -> Unit)? = null,
    val onShareRow: ((FeedRow) -> Unit)? = null,
    val onSelectSource: (Int) -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onModeChange: ((CatalogMode) -> Unit)? = null,
    val onRetry: (() -> Unit)? = null,
    val onLoadMore: (() -> Unit)? = null,
    val onOpenBoards: (() -> Unit)? = null,
    val onOpenSearch: (() -> Unit)? = null,
    val onOpenDownloads: (() -> Unit)? = null,
    val onOpenSaved: (() -> Unit)? = null,
    val onOpenWatched: (() -> Unit)? = null,
    val onOpenLibrary: (() -> Unit)? = onOpenWatched ?: onOpenSaved ?: onOpenDownloads,
    val onSettings: (() -> Unit)? = null,
    val onActivePreviewChanged: (String?) -> Unit = {},
    val onUpcomingPreviewChanged: (String?) -> Unit = {},
    val onChromeVisibleChange: (Boolean) -> Unit = {},
    val onCompactTitleVisibleChange: (Boolean) -> Unit = {},
)

/**
 * A board catalog's values. [rowAt] returns `null` for a row that is still loading; [rowKey]
 * must stay stable for a thread across loads. See [BoardScreen].
 */
@Immutable
data class BoardScreenState(
    val board: String,
    val description: String,
    val itemCount: Int,
    val rowAt: (Int) -> FeedRow?,
    val rowKey: (Int) -> Any = { index -> rowAt(index)?.id ?: "catalog-pending-$index" },
    val mode: CatalogMode = CatalogMode.LIST,
    val sortLabel: String? = null,
    val sortTabs: List<CatalogSortTab> = emptyList(),
    val selectedSortId: String? = null,
    val navigation: ReaderNavigation = ReaderNavigation.NONE,
    val reloading: Boolean = false,
    val wideWindow: Boolean = false,
    val selectedRowId: String? = null,
)

/** What a catalog can ask its host to do; a `null` action hides its control. Read-only. */
@Immutable
data class BoardScreenActions(
    val onOpenRow: (FeedRow) -> Unit = {},
    val onNavigation: () -> Unit = {},
    val onModeChange: ((CatalogMode) -> Unit)? = null,
    val onSort: () -> Unit = {},
    val onSelectSort: (String) -> Unit = {},
    val onOpenMedia: (() -> Unit)? = null,
    val onPickBoard: (() -> Unit)? = null,
    val onReload: (() -> Unit)? = null,
    val onSearch: (() -> Unit)? = null,
    val onOpenBrowser: (() -> Unit)? = null,
)
