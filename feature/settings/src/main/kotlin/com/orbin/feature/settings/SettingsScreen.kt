package com.orbin.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.designsystem.component.ModernFilterChip
import com.orbin.core.designsystem.component.ModernListItem
import com.orbin.core.designsystem.component.ModernSmallTopAppBar
import com.orbin.core.model.AppIconVariant
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.ColorTheme
import com.orbin.core.model.DohProvider
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.PreloadOption
import com.orbin.core.model.PreloadThrottleMode
import com.orbin.core.model.ThumbnailSize
import com.orbin.provider.api.ProviderMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val IMAGE_CACHE_LIMITS_MB = listOf(128, 256, 512, 1024)
private val CONNECT_TIMEOUTS_SECONDS = listOf(10L, 15L, 30L, 60L)
private val READ_TIMEOUTS_SECONDS = listOf(15L, 30L, 60L, 120L)

private const val DEFAULT_BACKUP_FILE_NAME = "orbin-backup.json"
private const val FONT_SCALE_SMALL = 0.9f
private const val FONT_SCALE_DEFAULT = 1f
private const val FONT_SCALE_LARGE = 1.1f
private const val FONT_SCALE_EXTRA_LARGE = 1.2f

/**
 * Settings screen. Sections follow what a setting *affects*, not which subsystem implements it:
 * Site, Content (what the feed shows), Notifications, Appearance, Media (playback and preloading),
 * Network & privacy, and Storage (bytes on disk, and getting them in or out).
 */
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
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val backupExporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                viewModel.exportBackup(appVersionName(context)) { backupJson ->
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(backupJson.toByteArray()) }
                            ?: error("Could not open the selected file for writing")
                    }
                }
            }
        }
    val backupImporter =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                viewModel.importBackup {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                            ?: error("Could not open the selected file for reading")
                    }
                }
            }
        }

    LaunchedEffect(backupStatus) {
        val status = backupStatus ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(status.message())
        viewModel.clearBackupStatus()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

            SectionHeader("Content")
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

            SectionHeader("Notifications")
            SwitchRow(
                "Thread watch notifications",
                settings.threadWatchNotificationsEnabled,
                viewModel::setThreadWatchNotifications,
                supporting = "Get notified when watched threads have new replies",
            )
            if (settings.threadWatchNotificationsEnabled) {
                TextFieldRow(
                    label = "Quiet hours start",
                    value = settings.quietHoursStart,
                    supporting = "HH:MM format (24-hour), leave empty to disable",
                    onValueChange = viewModel::setQuietHoursStart,
                )
                TextFieldRow(
                    label = "Quiet hours end",
                    value = settings.quietHoursEnd,
                    supporting = "HH:MM format (24-hour), leave empty to disable",
                    onValueChange = viewModel::setQuietHoursEnd,
                )
            }

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
            ModernListItem(
                title = "Clear local activity",
                subtitle = "Delete history, recent searches, and download history",
                trailing = {
                    IconButton(onClick = { showClearLocalActivityDialog = true }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Clear local activity")
                    }
                },
            )
            ModernListItem(
                title = "HTTPS only",
                subtitle = "Always enforced",
                trailing = { Switch(checked = true, onCheckedChange = null) },
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

            SectionHeader("Advanced")
            TextFieldRow(
                label = "Custom user agent",
                value = settings.userAgent,
                supporting = "Sent with every request. Leave empty to use Orbin's default.",
                onValueChange = viewModel::setUserAgent,
            )
            ChipChoiceRow(
                label = "Connect timeout",
                values = CONNECT_TIMEOUTS_SECONDS,
                selected = settings.connectTimeoutSeconds,
                text = { "$it s" },
                onChange = viewModel::setConnectTimeout,
            )
            ChipChoiceRow(
                label = "Read timeout",
                values = READ_TIMEOUTS_SECONDS,
                selected = settings.readTimeoutSeconds,
                text = { "$it s" },
                onChange = viewModel::setReadTimeout,
            )
            SwitchRow(
                "Check certificate revocation",
                !settings.disableOcspChecking,
                viewModel::setCertificateRevocationChecks,
                supporting =
                    "Asks each certificate authority whether a site's certificate has been revoked. " +
                        "Off by default: the check is slow and many networks block it, which shows up " +
                        "as sites failing to load rather than as a warning.",
            )
            SupportingNote(
                "Timeouts and revocation checking are applied when the network client is built, " +
                    "so changes to them take effect the next time Orbin starts.",
            )

            SectionHeader("Storage")
            ModernListItem(
                title = "Downloads",
                subtitle = "View download history",
                onClick = onOpenDownloads,
            )
            ModernListItem(
                title = "Saved media folder",
                subtitle = settings.downloadFolderUri.ifBlank { "Downloads/Orbin" },
                onClick = { folderPicker.launch(null) },
            )
            ChipChoiceRow(
                label = "Image cache limit",
                values = IMAGE_CACHE_LIMITS_MB,
                selected = settings.imageCacheLimitMb,
                text = { "$it MB" },
                onChange = viewModel::setImageCacheLimitMb,
            )
            ModernListItem(
                title = "Export data",
                subtitle = "Save settings, boards and bookmarks to a file",
                onClick = { backupExporter.launch(DEFAULT_BACKUP_FILE_NAME) },
            )
            ModernListItem(
                title = "Import data",
                subtitle = "Restore settings, boards and bookmarks from a backup",
                onClick = { backupImporter.launch(arrayOf("application/json", "*/*")) },
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

@Composable
private fun <T> ChipChoiceRow(
    label: String,
    values: List<T>,
    selected: T,
    text: (T) -> String,
    onChange: (T) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        LazyRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            items(values) { value ->
                ModernFilterChip(
                    label = text(value),
                    selected = value == selected,
                    onSelectedChange = { onChange(value) },
                    modifier = Modifier.padding(end = 8.dp),
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
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

@Composable
private fun SupportingNote(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    supporting: String? = null,
) {
    ModernListItem(
        title = label,
        subtitle = supporting,
        trailing = { Switch(checked = checked, onCheckedChange = onChange) },
        backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    )
}

@Composable
private fun TextFieldRow(
    label: String,
    value: String,
    supporting: String,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxWidth(),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            supportingText = { Text(supporting) },
            singleLine = false,
            shape = MaterialTheme.shapes.medium,
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

@Composable
private fun <T> ChoiceRow(
    label: String,
    values: List<T>,
    selected: T,
    text: (T) -> String,
    onChange: (T) -> Unit,
) {
    ChipChoiceRow(
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

private fun BackupStatus.message(): String =
    when (this) {
        BackupStatus.Exported -> "Backup saved"
        is BackupStatus.Imported ->
            "Restored ${summary.subscribedBoards} boards, ${summary.bookmarks} bookmarks and " +
                "${summary.savedSearches} saved searches"
        is BackupStatus.Failed -> message
    }

private fun appVersionName(context: android.content.Context): String =
    runCatching {
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            .orEmpty()
    }.getOrDefault("")
