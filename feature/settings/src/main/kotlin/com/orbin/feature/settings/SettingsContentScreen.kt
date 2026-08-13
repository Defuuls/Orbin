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
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.designsystem.component.ModernListItem
import com.orbin.core.designsystem.component.ModernSmallTopAppBar
import com.orbin.core.model.FeedRefreshInterval
import com.orbin.core.model.FeedThreadLimit

/** What the subscribed feed shows and how boards feed into it: tags, thresholds, and the wizard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContentScreen(
    onBack: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenSetup: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            ModernSmallTopAppBar(
                title = "Content & Feed",
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
            SwitchRow(
                "Personalized home feed",
                settings.personalizedHomeFeed,
                viewModel::setPersonalizedHomeFeed,
            )
            ModernListItem(
                title = "Subscriptions",
                subtitle = "Manage subscribed boards",
                onClick = onOpenSubscriptions,
            )
            TextFieldRow(
                label = "Hidden tags",
                value = settings.hiddenTags,
                supporting =
                    "Hidden tags are removed from the feed, board catalogs and thread replies. " +
                        "Separate tags with commas. Prefix with name:, cap:, trip: or id: to hide " +
                        "by who posted instead of what was posted.",
                onValueChange = viewModel::setHiddenTags,
            )
            TextFieldRow(
                label = "Muted tags",
                value = settings.mutedTags,
                supporting = "Muted tags stay visible but get de-emphasized in the feed.",
                onValueChange = viewModel::setMutedTags,
            )
            SwitchRow("Hide NSFW boards", settings.hideNsfwBoards, viewModel::setHideNsfwBoards)
            SwitchRow(
                "Hide text-only threads",
                settings.hideTextOnlyThreads,
                viewModel::setHideTextOnlyThreads,
            )
            ChoiceRow(
                label = "Refresh feed on return",
                values = FeedRefreshInterval.entries,
                selected = settings.feedRefreshInterval,
                text = { it.label },
                onChange = viewModel::setFeedRefreshInterval,
            )
            SupportingNote(
                "How stale the feed may be before coming back to it reloads it — after reading a " +
                    "thread, say. \"Always\" reloads every time; \"Never\" keeps the feed exactly " +
                    "as you left it.",
            )
            ChoiceRow(
                label = "Threads per board",
                values = FeedThreadLimit.entries,
                selected = settings.feedThreadLimit,
                text = { it.label },
                onChange = viewModel::setFeedThreadLimit,
            )
            ModernListItem(
                title = "Run setup again",
                subtitle = "Subscriptions, preferences, and privacy",
                trailing = {
                    Switch(
                        checked = false,
                        onCheckedChange = { enabled ->
                            if (enabled) onOpenSetup()
                        },
                    )
                },
            )
        }
    }
}
