package com.orbin.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.DohProvider
import com.orbin.core.model.FeedThreadLimit

/** Settings screen covering appearance, media, and network/privacy sections. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenSetup: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                }
                viewModel.setDownloadFolderUri(uri.toString())
            }
        }

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
            SectionHeader("Content")
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenSubscriptions),
                headlineContent = { Text("Subscriptions") },
                supportingContent = { Text("Manage subscribed boards") },
            )
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenSetup),
                headlineContent = { Text("Run setup again") },
                supportingContent = { Text("Subscriptions, preferences, and privacy") },
                trailingContent = {
                    Switch(
                        checked = false,
                        onCheckedChange = { enabled ->
                            if (enabled) onOpenSetup()
                        },
                    )
                },
            )

            SectionHeader("Appearance")
            ThemeModeRow(settings.themeMode, viewModel::setThemeMode)
            SwitchRow("Dynamic color", settings.dynamicColor, viewModel::setDynamicColor)
            SwitchRow("AMOLED black", settings.amoled, viewModel::setAmoled)

            SectionHeader("Media")
            SwitchRow("Autoplay videos", settings.autoplayVideos, viewModel::setAutoplay)
            SwitchRow("Mute by default", settings.muteByDefault, viewModel::setMute)
            SwitchRow("Preload images", settings.preloadImages, viewModel::setPreload)
            ChoiceRow(
                label = "Threads per board",
                values = FeedThreadLimit.entries,
                selected = settings.feedThreadLimit,
                text = { it.label },
                onChange = viewModel::setFeedThreadLimit,
            )

            SectionHeader("Network & privacy")
            SwitchRow("Lock with biometrics", settings.biometricLockEnabled, viewModel::setBiometricLock)
            SwitchRow("Save recent searches", settings.saveRecentSearches, viewModel::setSaveRecentSearches)
            ListItem(
                headlineContent = { Text("HTTPS only") },
                supportingContent = { Text("Always enforced") },
                trailingContent = { Switch(checked = true, onCheckedChange = null) },
            )
            SwitchRow("DNS over HTTPS", settings.dohEnabled, viewModel::setDoh)
            if (settings.dohEnabled) {
                ChoiceRow(
                    label = "DNS provider",
                    values = DohProvider.entries,
                    selected = settings.dohProvider,
                    text = { it.label },
                    onChange = viewModel::setDohProvider,
                )
            }

            SectionHeader("Storage")
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenDownloads),
                headlineContent = { Text("Downloads") },
                supportingContent = { Text("View download history") },
            )
            ListItem(
                modifier = Modifier.clickable { folderPicker.launch(null) },
                headlineContent = { Text("Saved media folder") },
                supportingContent = {
                    Text(settings.downloadFolderUri.ifBlank { "Downloads/Orbin" })
                },
            )
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
    ChoiceRow(
        label = "Theme",
        values = AppThemeMode.entries,
        selected = current,
        text = { it.name.lowercase().replaceFirstChar { char -> char.uppercase() } },
        onChange = onChange,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    label: String,
    values: List<T>,
    selected: T,
    text: (T) -> String,
    onChange: (T) -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onChange(value) },
                label = { Text(text(value)) },
            )
        }
    }
}
