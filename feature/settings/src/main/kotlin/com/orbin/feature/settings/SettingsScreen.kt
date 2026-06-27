package com.orbin.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.AppThemeMode

/** Settings screen covering appearance, media, and network/privacy sections. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("Appearance")
            ThemeModeRow(settings.themeMode, viewModel::setThemeMode)
            SwitchRow("Dynamic color", settings.dynamicColor, viewModel::setDynamicColor)
            SwitchRow("AMOLED black", settings.amoled, viewModel::setAmoled)

            SectionHeader("Media")
            SwitchRow("Autoplay videos", settings.autoplayVideos, viewModel::setAutoplay)
            SwitchRow("Mute by default", settings.muteByDefault, viewModel::setMute)
            SwitchRow("Preload images", settings.preloadImages, viewModel::setPreload)

            SectionHeader("Network & privacy")
            SwitchRow("HTTPS only", settings.httpsOnly, viewModel::setHttpsOnly)
            SwitchRow("DNS over HTTPS", settings.dohEnabled, viewModel::setDoh)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeRow(
    current: AppThemeMode,
    onChange: (AppThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppThemeMode.entries.forEach { mode ->
            FilterChip(
                selected = current == mode,
                onClick = { onChange(mode) },
                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
            )
        }
    }
}
