package com.orbin.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.uinext.InlineAction
import com.orbin.uinext.nextFrosted
import com.orbin.uinext.tokens.NextMotion
import com.orbin.uinext.tokens.NextRadius
import com.orbin.uinext.tokens.NextSpace

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
    railAction: String = stringResource(com.orbin.uinext.R.string.next_action_search),
    onOpenBoards: (() -> Unit)? = null,
    onOpenMedia: (() -> Unit)? = null,
) {
    val switcherViewModel: FeedSiteSwitcherViewModel = hiltViewModel()
    val activeProviderId by switcherViewModel.activeProviderId.collectAsStateWithLifecycle()
    var compactTitleVisible by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        NextFeedScreen(
            onOpenThread = onOpenThread,
            onOpenCommands = onOpenCommands,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.fillMaxSize(),
            showRail = showRail,
            hideRailOnScroll = hideRailOnScroll,
            onChromeVisibleChange = onChromeVisibleChange,
            onCompactTitleVisibleChange = { compactTitleVisible = it },
            scrollToTopRequest = scrollToTopRequest,
            refreshRequest = refreshRequest,
            filter = filter,
            onClearFilter = onClearFilter,
            railAction = railAction,
            onOpenBoards = onOpenBoards,
            onOpenMedia = onOpenMedia,
        )

        if (switcherViewModel.sites.size > 1) {
            AnimatedVisibility(
                visible = !compactTitleVisible,
                enter = fadeIn(tween(NextMotion.CHROME_MS)),
                exit = fadeOut(tween(NextMotion.CHROME_MS)),
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                val shape = RoundedCornerShape(NextRadius.pill)
                Row(
                    modifier =
                        Modifier
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                            ).padding(top = 12.dp, end = NextSpace.chromeInset)
                            .nextFrosted(shape)
                            .selectableGroup()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
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
