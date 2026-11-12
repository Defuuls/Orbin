@file:Suppress("CyclomaticComplexMethod")

package com.orbin.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.UpdateStatus
import com.orbin.uinext.NextSnackbarHostState
import com.orbin.uinext.NextTheme
import com.orbin.uinext.SettingItem
import com.orbin.uinext.SettingKind
import com.orbin.uinext.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CLEAR_ARMED_MS = 4_000L
private const val BACKUP_FILE_NAME = "orbin-backup.json"
private const val BYTES_PER_MB = 1024L * 1024L

/**
 * A settings directory backed by the same persisted controls on every subpage.
 * System actions such as backup import/export stay in the platform layer.
 */
@Composable
fun NextSettingsScreen(
    snackbarHostState: NextSnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
    onNavigate: ((String) -> Unit)? = null,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val availablePlaybackScopes by viewModel.playbackScopes.collectAsStateWithLifecycle()
    val playbackScopes =
        remember(availablePlaybackScopes, settings.playbackPreferences.overrides) {
            (
                availablePlaybackScopes +
                    settings.playbackPreferences.overrides.map { override ->
                        val site =
                            viewModel.providers.firstOrNull { it.id.value == override.scope.providerId }?.displayName
                                ?: override.scope.providerId
                        PlaybackScopeChoice(
                            override.scope,
                            if (override.scope.isBoard) "$site · /${override.scope.boardId}/" else site,
                        )
                    }
            ).distinctBy { it.scope }
        }
    var playbackProvider by rememberSaveable { mutableStateOf("") }
    var playbackBoard by rememberSaveable { mutableStateOf("") }
    val playbackScope = PlaybackScope(playbackProvider, playbackBoard)
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val updateCheck by viewModel.updateCheck.collectAsStateWithLifecycle()
    val checkUpdatesOnLaunch by viewModel.checkUpdatesOnLaunch.collectAsStateWithLifecycle()
    val strictDns by viewModel.strictDns.collectAsStateWithLifecycle()
    val dnsFallbackActive by viewModel.dnsFallbackActive.collectAsStateWithLifecycle()
    val imageCacheUsageBytes by viewModel.imageCacheUsageBytes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val strings = remember(context) { context.settingsStrings() }
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var clearArmed by remember { mutableStateOf(false) }
    LaunchedEffect(clearArmed) {
        if (clearArmed) {
            delay(CLEAR_ARMED_MS)
            clearArmed = false
        }
    }

    // The ViewModel produces the text and never sees a SAF URI; writing the file is this side's job.
    val backupExporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            viewModel.exportBackup(appVersionName(context)) { json ->
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        ?: error("Could not open the selected file for writing")
                }
            }
        }
    val backupImporter =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            viewModel.importBackup {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBackupText() }
                        ?: error("Could not open the selected file for reading")
                }
            }
        }

    LaunchedEffect(backupStatus) {
        val status = backupStatus ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(status.message(strings))
        viewModel.clearBackupStatus()
    }
    // The row itself reports progress; the snackbar says how a finished check went. A newer
    // release says so in the app-wide update dialog instead, which can install it.
    LaunchedEffect(updateCheck) {
        val message = updateCheck.snackbarMessage(strings) ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message, withDismissAction = true)
        viewModel.clearUpdateCheck()
    }

    val cacheLabel = imageCacheUsageBytes.cacheSizeLabel(strings)
    val model =
        remember(
            settings,
            updateCheck,
            cacheLabel,
            clearArmed,
            checkUpdatesOnLaunch,
            strictDns,
            dnsFallbackActive,
        ) {
            buildSettings(
                settings,
                viewModel,
                updateCheck.rowValue(context, strings),
                strings,
                cacheLabel,
                clearArmed,
                checkUpdatesOnLaunch,
                DnsRowState(strict = strictDns, fallbackActive = dnsFallbackActive),
            )
        }

    NextTheme {
        SettingsScreen(
            groups = model.groups + ("Playback" to playbackRows(settings, playbackScopes, playbackScope)),
            onBack = onBack,
            onNavigate = onNavigate,
            enabledSiteCount = viewModel.providers.size,
            expandedId = expanded,
            onActivate = { item ->
                if (item.id == "playbackScope") viewModel.loadPlaybackBoards()
                val preferences = settings.playbackPreferences
                val playback = preferences.forScope(playbackScope)
                val updatedPlayback =
                    when (item.id) {
                        "startMuted" -> playback.copy(startMuted = !playback.startMuted)
                        "loopVideos" -> playback.copy(loopVideos = !playback.loopVideos)
                        "inheritPlayback" -> if (preferences.hasOverride(playbackScope)) null else playback
                        else -> null
                    }
                if (item.id == "openThreadComments") {
                    viewModel.setOpenThreadComments(!settings.openThreadComments)
                } else if (item.id in
                    setOf("startMuted", "loopVideos", "inheritPlayback", "resetPlayback")
                ) {
                    viewModel.setPlaybackSettings(playbackScope, updatedPlayback)
                } else {
                    when (item.kind) {
                        SettingKind.TOGGLE -> model.toggle(item.id)
                        SettingKind.CHOICE, SettingKind.TEXT ->
                            expanded = if (expanded == item.id) null else item.id
                        SettingKind.ACTION ->
                            dispatch(
                                item = item,
                                onExport = { backupExporter.launch(BACKUP_FILE_NAME) },
                                onImport = { backupImporter.launch(arrayOf("application/json", "*/*")) },
                                onClear = {
                                    if (clearArmed) {
                                        clearArmed = false
                                        viewModel.clearLocalActivity { cleared ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    strings.get(
                                                        if (cleared) {
                                                            R.string.settings_clear_activity_done
                                                        } else {
                                                            R.string.settings_clear_activity_failed
                                                        },
                                                    ),
                                                )
                                            }
                                        }
                                    } else {
                                        clearArmed = true
                                    }
                                },
                                onClearImageCache = viewModel::clearImageCache,
                                onCheckUpdates = {
                                    if (updateCheck != UpdateCheckState.Checking) {
                                        viewModel.checkForUpdate(appVersionName(context))
                                    }
                                },
                            )
                        SettingKind.INFO -> Unit
                    }
                }
            },
            onSelectOption = { item, index ->
                if (item.id == "playbackScope") {
                    playbackScopes.getOrNull(index)?.scope?.let {
                        playbackProvider = it.providerId
                        playbackBoard = it.boardId
                    }
                } else if (item.id == "autoplay" && index in 0..1) {
                    viewModel.setPlaybackSettings(
                        playbackScope,
                        settings.playbackPreferences.forScope(playbackScope).copy(autoplay = index == 1),
                    )
                } else if (item.id == "preloadNextClip" && index in 0..1) {
                    viewModel.setPlaybackSettings(
                        playbackScope,
                        settings.playbackPreferences.forScope(playbackScope).copy(preloadNextClip = index == 1),
                    )
                } else {
                    model.choose(item.id, index)
                }
                expanded = null
            },
            onCommitText = { item, value ->
                model.commit(item.id, value)
                expanded = null
            },
            modifier = modifier,
        )
    }
}

private fun dispatch(
    item: SettingItem,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClear: () -> Unit,
    onClearImageCache: () -> Unit,
    onCheckUpdates: () -> Unit,
) {
    when (item.id) {
        "exportBackup" -> onExport()
        "importBackup" -> onImport()
        "clearActivity" -> onClear()
        "clearImageCache" -> onClearImageCache()
        "checkUpdates" -> onCheckUpdates()
    }
}

private fun Long.cacheSizeLabel(strings: SettingsStrings): String {
    if (this <= 0L) return strings.get(R.string.settings_image_cache_empty)
    val megabytes = this.toDouble() / BYTES_PER_MB
    return if (megabytes < 1.0) {
        strings.get(R.string.settings_image_cache_small)
    } else {
        strings.get(R.string.settings_image_cache_size, megabytes.toInt())
    }
}

private fun BackupStatus.message(strings: SettingsStrings): String =
    when (this) {
        BackupStatus.Exported -> strings.get(R.string.settings_backup_saved)
        is BackupStatus.Imported ->
            if (summary.skippedUnknownProvider > 0) {
                strings.get(
                    R.string.settings_backup_restored_skipped,
                    summary.subscribedBoards,
                    summary.bookmarks,
                    summary.savedSearches,
                    summary.skippedUnknownProvider,
                )
            } else {
                strings.get(
                    R.string.settings_backup_restored,
                    summary.subscribedBoards,
                    summary.bookmarks,
                    summary.savedSearches,
                )
            }
        is BackupStatus.Failed -> message
    }

private fun UpdateCheckState.snackbarMessage(strings: SettingsStrings): String? =
    when (this) {
        UpdateCheckState.Idle, UpdateCheckState.Checking -> null
        is UpdateCheckState.Failed -> strings.get(R.string.settings_check_failed_message, message)
        is UpdateCheckState.Result ->
            when (status) {
                UpdateStatus.UpToDate -> strings.get(R.string.settings_up_to_date_message)
                // Shown by the update dialog rather than a snackbar.
                is UpdateStatus.Available -> null
            }
    }

private fun UpdateCheckState.rowValue(
    context: android.content.Context,
    strings: SettingsStrings,
): String =
    when (this) {
        UpdateCheckState.Idle -> appVersionName(context).ifBlank { strings.get(R.string.settings_unknown_build) }
        UpdateCheckState.Checking -> strings.get(R.string.settings_checking)
        is UpdateCheckState.Failed -> strings.get(R.string.settings_check_failed)
        is UpdateCheckState.Result ->
            when (status) {
                UpdateStatus.UpToDate -> strings.get(R.string.settings_up_to_date)
                is UpdateStatus.Available -> strings.get(R.string.settings_update_available, status.tag)
            }
    }
