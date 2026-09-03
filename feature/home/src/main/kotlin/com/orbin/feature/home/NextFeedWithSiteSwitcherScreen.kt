package com.orbin.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.uinext.InlineAction

@Composable
fun NextFeedWithSiteSwitcherScreen(
    onOpenThread: (provider: String, board: String, thread: Long, title: String) -> Unit,
    onOpenCommands: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    showRail: Boolean = true,
    hideRailOnScroll: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    scrollToTopRequest: Int = 0,
    refreshRequest: Int = 0,
    filter: String = "",
    onClearFilter: () -> Unit = {},
    railAction: String = "Search",
    switcherViewModel: FeedSiteSwitcherViewModel = hiltViewModel(),
) {
    val activeProviderId by switcherViewModel.activeProviderId.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        NextFeedScreen(
            onOpenThread = onOpenThread,
            onOpenCommands = onOpenCommands,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.fillMaxSize(),
            showRail = showRail,
            hideRailOnScroll = hideRailOnScroll,
            onChromeVisibleChange = onChromeVisibleChange,
            scrollToTopRequest = scrollToTopRequest,
            refreshRequest = refreshRequest,
            filter = filter,
            onClearFilter = onClearFilter,
            railAction = railAction,
        )

        if (switcherViewModel.sites.size > 1) {
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                        ).padding(top = 12.dp, end = 14.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.selectableGroup().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    switcherViewModel.sites.forEach { site ->
                        InlineAction(
                            label = site.label,
                            selected = site.id == activeProviderId,
                            onClick = { switcherViewModel.selectSite(site.id) },
                        )
                    }
                }
            }
        }
    }
}
