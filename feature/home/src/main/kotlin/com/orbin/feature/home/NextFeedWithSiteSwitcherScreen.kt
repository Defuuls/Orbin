package com.orbin.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.uinext.PlatformSegments

@Composable
fun NextFeedWithSiteSwitcherScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    showRail: Boolean = true,
    hideRailOnScroll: Boolean = true,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    onOpenBoards: (() -> Unit)? = null,
    onOpenMedia: (() -> Unit)? = null,
) {
    val switcherViewModel: FeedSiteSwitcherViewModel = hiltViewModel()
    val activeProviderId by switcherViewModel.activeProviderId.collectAsStateWithLifecycle()
    NextFeedScreen(
        onOpenThread = onOpenThread,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        showRail = showRail,
        hideRailOnScroll = hideRailOnScroll,
        onChromeVisibleChange = onChromeVisibleChange,
        onOpenBoards = onOpenBoards,
        onOpenMedia = onOpenMedia,
        headerContent = {
            if (switcherViewModel.sites.size > 1) {
                PlatformSegments(
                    labels = switcherViewModel.sites.map { it.label },
                    selected = switcherViewModel.sites.indexOfFirst { it.id == activeProviderId },
                    onSelect = { switcherViewModel.selectSite(switcherViewModel.sites[it].id) },
                )
            }
        },
    )
}
