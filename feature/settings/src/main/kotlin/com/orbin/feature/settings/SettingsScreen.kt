package com.orbin.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.designsystem.component.ModernListItem
import com.orbin.core.designsystem.component.ModernSmallTopAppBar
import com.orbin.provider.api.ProviderMetadata

/**
 * Settings hub. Rather than one long scroll of every option, this page lists categories and each
 * opens its own focused screen — the same split every settings-heavy app converges on once the
 * option count outgrows a single page. "Site" is the one exception: with only one control, it
 * does not earn a page of its own, so it stays inline here when more than one provider exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenContent: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenStorage: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            ModernSmallTopAppBar(
                title = "Settings",
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
            if (viewModel.providers.size > 1) {
                SectionHeader("Site")
                ChipChoiceRow(
                    label = "Active provider",
                    values = viewModel.providers,
                    selected = activeProvider,
                    text = ProviderMetadata::displayName,
                    onChange = { metadata -> viewModel.setActiveProvider(metadata.id) },
                )
            }

            CategoryRow(
                icon = Icons.Filled.DynamicFeed,
                title = "Content & Feed",
                subtitle = "Subscriptions, tags, and how the feed refreshes",
                onClick = onOpenContent,
            )
            CategoryRow(
                icon = Icons.Filled.Notifications,
                title = "Notifications",
                subtitle = "Thread watch alerts and quiet hours",
                onClick = onOpenNotifications,
            )
            CategoryRow(
                icon = Icons.Filled.Palette,
                title = "Appearance",
                subtitle = "Theme, color, layout, and text size",
                onClick = onOpenAppearance,
            )
            CategoryRow(
                icon = Icons.Filled.PlayCircle,
                title = "Media & Playback",
                subtitle = "Video, images, and background preloading",
                onClick = onOpenMedia,
            )
            CategoryRow(
                icon = Icons.Filled.Security,
                title = "Privacy & Network",
                subtitle = "Locking, local data, updates, and DNS",
                onClick = onOpenPrivacy,
            )
            CategoryRow(
                icon = Icons.Filled.Save,
                title = "Storage & Backup",
                subtitle = "Downloads, cache limits, and data export",
                onClick = onOpenStorage,
            )
        }
    }
}

@Composable
private fun CategoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ModernListItem(
        title = title,
        subtitle = subtitle,
        leading = {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        onClick = onClick,
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
