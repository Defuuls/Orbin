package com.orbin.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.AppIconVariant
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.ColorTheme
import com.orbin.core.model.DohProvider
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.PreloadOption
import com.orbin.core.model.PreloadThrottleMode
import com.orbin.core.model.ThumbnailSize
import com.orbin.provider.api.ProviderMetadata

private const val FONT_SCALE_SMALL = 0.9f
private const val FONT_SCALE_DEFAULT = 1f
private const val FONT_SCALE_LARGE = 1.1f
private const val FONT_SCALE_EXTRA_LARGE = 1.2f

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
    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showClearLocalActivityDialog by remember { mutableStateOf(false) }
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
            if (viewModel.providers.size > 1) {
                SectionHeader("Site")
                DropdownChoiceRow(
                    label = "Active provider",
                    values = viewModel.providers,
                    selected = activeProvider,
                    text = ProviderMetadata::displayName,
                    onChange = { metadata -> viewModel.setActiveProvider(metadata.id) },
                )
            }

            SectionHeader("Content")
            SwitchRow(
                "Personalized home feed",
                settings.personalizedHomeFeed,
                viewModel::setPersonalizedHomeFeed,
            )
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenSubscriptions),
                headlineContent = { Text("Subscriptions") },
                supportingContent = { Text("Manage subscribed boards") },
            )
            TextFieldRow(
                label = "Hidden tags",
                value = settings.hiddenTags,
                supporting = "Hidden tags are removed from feeds. Separate tags with commas.",
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
            SwitchRow(
                "Refresh feed on return",
                settings.refreshFeedOnReturn,
                viewModel::setRefreshFeedOnReturn,
                supporting =
                    "Reload subscriptions and threads when coming back to the feed, " +
                        "for example after reading a thread. Turn off to keep the feed as you left it.",
            )
            SwitchRow(
                "Verify file host links",
                settings.verifyFileHostLinks,
                viewModel::setVerifyFileHostLinks,
                supporting =
                    "Check gofile.io, fast-file.ru, and mega.nz links in posts and mark them " +
                        "with a green check when they exist or a red cross when they are gone.",
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
            ColorThemeRow(settings.colorTheme, viewModel::setColorTheme)
            AppIconVariantRow(settings.appIconVariant, viewModel::setAppIconVariant)
            ThemeModeRow(settings.themeMode, viewModel::setThemeMode)
            SwitchRow("Dynamic color", settings.dynamicColor, viewModel::setDynamicColor)
            SwitchRow("AMOLED black", settings.amoled, viewModel::setAmoled)
            SwitchRow(
                "Full-screen feed",
                settings.fullScreenFeedChrome,
                viewModel::setFullScreenFeedChrome,
                supporting = "Hide the board headers, feed bars, and system bars so the feed fills the whole screen.",
            )
            ChoiceRow(
                label = "Font size",
                values = FontScaleOption.entries,
                selected = FontScaleOption.fromScale(settings.fontScale),
                text = { it.label },
                onChange = { option -> viewModel.setFontScale(option.scale) },
            )
            ChoiceRow(
                label = "Thumbnail size",
                values = ThumbnailSize.entries,
                selected = settings.thumbnailSize,
                text = { it.label },
                onChange = viewModel::setThumbnailSize,
            )

            SectionHeader("Media")
            SwitchRow("Autoplay videos", settings.autoplayVideos, viewModel::setAutoplay)
            SwitchRow("Mute by default", settings.muteByDefault, viewModel::setMute)
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
            ChoiceRow(
                label = "Threads per board",
                values = FeedThreadLimit.entries,
                selected = settings.feedThreadLimit,
                text = { it.label },
                onChange = viewModel::setFeedThreadLimit,
            )

            SectionHeader("Network & privacy")
            SwitchRow(
                "Lock with biometrics",
                settings.biometricLockEnabled,
                viewModel::setBiometricLock,
            )
            SwitchRow(
                "Save recent searches",
                settings.saveRecentSearches,
                viewModel::setSaveRecentSearches,
            )
            SwitchRow(
                "Internal updater",
                settings.internalUpdaterEnabled,
                viewModel::setInternalUpdater,
                supporting = "Check for Orbin updates inside the app",
            )
            ListItem(
                headlineContent = { Text("Clear local activity") },
                supportingContent = { Text("Delete history, recent searches, and download history") },
                trailingContent = {
                    IconButton(onClick = { showClearLocalActivityDialog = true }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Clear local activity")
                    }
                },
            )
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

    if (showClearLocalActivityDialog) {
        AlertDialog(
            onDismissRequest = { showClearLocalActivityDialog = false },
            title = { Text("Clear local activity?") },
            text = {
                Text("This deletes browsing history, recent searches, and download history stored on this device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearLocalActivity()
                        showClearLocalActivityDialog = false
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                Button(onClick = { showClearLocalActivityDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownChoiceRow(
    label: String,
    values: List<T>,
    selected: T,
    text: (T) -> String,
    onChange: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = text(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(text(value)) },
                    onClick = {
                        expanded = false
                        onChange(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun ColorThemeRow(
    current: ColorTheme,
    onChange: (ColorTheme) -> Unit,
) {
    ChoiceRow(
        label = "Color theme",
        values = ColorTheme.entries,
        selected = current,
        text = { it.name.lowercase().replaceFirstChar { char -> char.uppercase() } },
        onChange = onChange,
    )
}

@Composable
private fun AppIconVariantRow(
    current: AppIconVariant,
    onChange: (AppIconVariant) -> Unit,
) {
    ChoiceRow(
        label = "App icon",
        values = AppIconVariant.entries,
        selected = current,
        text = { it.name.lowercase().replaceFirstChar { char -> char.uppercase() } },
        onChange = onChange,
    )
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
    supporting: String? = null,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
private fun TextFieldRow(
    label: String,
    value: String,
    supporting: String,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            supportingText = { Text(supporting) },
            singleLine = false,
        )
    }
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

/**
 * A single-choice setting. Previously rendered as a horizontally scrolling row of chips; now a
 * compact exposed dropdown so long option lists (e.g. the ported imageboard themes) stay usable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    label: String,
    values: List<T>,
    selected: T,
    text: (T) -> String,
    onChange: (T) -> Unit,
) {
    DropdownChoiceRow(
        label = label,
        values = values,
        selected = selected,
        text = text,
        onChange = onChange,
    )
}

private enum class FontScaleOption(
    val scale: Float,
    val label: String,
) {
    SMALL(FONT_SCALE_SMALL, "Small"),
    DEFAULT(FONT_SCALE_DEFAULT, "Default"),
    LARGE(FONT_SCALE_LARGE, "Large"),
    XLARGE(FONT_SCALE_EXTRA_LARGE, "XL"),
    ;

    companion object {
        fun fromScale(scale: Float): FontScaleOption =
            entries.minByOrNull { option -> kotlin.math.abs(option.scale - scale) } ?: DEFAULT
    }
}
