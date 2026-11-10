package com.orbin.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun NextFeedWithSiteSwitcherScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    showRail: Boolean = true,
    hideRailOnScroll: Boolean = true,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    onOpenBoards: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    onOpenSearch: (() -> Unit)? = null,
    onOpenSaved: (() -> Unit)? = null,
    onOpenWatched: (() -> Unit)? = null,
    onOpenBoard: ((provider: String, board: String, title: String) -> Unit)? = null,
    onOpenThreadVideos: (provider: String, board: String, thread: Long, title: String) -> Unit = onOpenThread,
) {
    val switcherViewModel: FeedSiteSwitcherViewModel = hiltViewModel()
    val activeProviderId by switcherViewModel.activeProviderId.collectAsStateWithLifecycle()
    NextFeedScreen(
        onOpenThread = onOpenThread,
        onOpenThreadVideos = onOpenThreadVideos,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        showRail = showRail,
        hideRailOnScroll = hideRailOnScroll,
        onChromeVisibleChange = onChromeVisibleChange,
        onOpenBoards = onOpenBoards,
        onOpenDownloads = onOpenDownloads,
        onOpenSearch = onOpenSearch,
        onOpenSaved = onOpenSaved,
        onOpenWatched = onOpenWatched,
        onOpenBoard = onOpenBoard,
        providerFilter = activeProviderId,
        sourceLabels = listOf(stringResource(R.string.feed_all_sites)) + switcherViewModel.sites.map { it.label },
        selectedSource = switcherViewModel.sites.indexOfFirst { it.id == activeProviderId } + 1,
        onSelectSource = { switcherViewModel.selectSite(switcherViewModel.sites.getOrNull(it - 1)?.id) },
    )
}
