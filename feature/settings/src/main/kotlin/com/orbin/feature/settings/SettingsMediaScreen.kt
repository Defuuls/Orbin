package com.orbin.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.designsystem.component.ModernSmallTopAppBar
import com.orbin.core.model.MediaFilter
import com.orbin.core.model.PreloadOption
import com.orbin.core.model.PreloadThrottleMode

/** Video and image playback behavior, plus background preloading. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMediaScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            ModernSmallTopAppBar(
                title = "Media & Playback",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
        ) {
            ChoiceRow(
                label = "Show media",
                values = MediaFilter.entries,
                selected = settings.mediaFilter,
                text = { it.label },
                onChange = viewModel::setMediaFilter,
            )
            SupportingNote(
                "Applies everywhere media is shown — the subscribed feed, board catalogs, thread " +
                    "view and the galleries. Attachments of the other kind are hidden, and threads " +
                    "left with nothing to show drop out of the feed and catalogs.",
            )
            SwitchRow("Autoplay videos", settings.autoplayVideos, viewModel::setAutoplay)
            SwitchRow("Mute by default", settings.muteByDefault, viewModel::setMute)
            SwitchRow(
                "Fullscreen video",
                settings.fullscreenVideoPlayback,
                viewModel::setFullscreenVideoPlayback,
                supporting = "Play videos edge-to-edge, hiding the status bar and app chrome.",
            )
            SwitchRow(
                "Auto-rotate video",
                settings.autoRotateVideoFullscreen,
                viewModel::setAutoRotateVideoFullscreen,
                supporting = "Turn the screen to landscape automatically when a wide video starts playing.",
            )
            SwitchRow(
                "Media scroll in thread",
                settings.mediaScrollThreadView,
                viewModel::setMediaScrollThreadView,
                supporting = "Swipe to scroll through multiple attachments in thread view.",
            )
            SwitchRow(
                "Media scroll in board",
                settings.mediaScrollBoardView,
                viewModel::setMediaScrollBoardView,
                supporting = "Swipe to scroll through multiple attachments in board view.",
            )
            SwitchRow(
                "Autoplay videos in feed",
                settings.autoplayVideosInFeed,
                viewModel::setAutoplayVideosInFeed,
                supporting =
                    "Play each thread's first video automatically as it scrolls into view in the " +
                        "subscribed feed, muted per \"Mute by default\" above. Nothing else about the thread opens.",
            )
            SwitchRow("Preload images", settings.preloadImages, viewModel::setPreload)
            ChoiceRow(
                label = "Preload content",
                values = PreloadOption.entries,
                selected = settings.preloadOption,
                text = { it.label },
                onChange = viewModel::setPreloadOption,
            )
            ChoiceRow(
                label = "Preload speed",
                values = PreloadThrottleMode.entries,
                selected = settings.preloadThrottleMode,
                text = { it.label },
                onChange = viewModel::setPreloadThrottleMode,
            )
        }
    }
}
