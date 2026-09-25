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
import com.orbin.core.common.link.SafeExternalLinks
import com.orbin.core.model.UpdateStatus
import com.orbin.uinext.NextSnackbarHostState
import com.orbin.uinext.NextSnackbarResult
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
 * Every setting on one screen, and every one of them editable on it.
 *
 * The list used to hand eleven of its rows back to the category screens it replaced, which meant
 * pressing a setting could drop you into the interface this one was built to replace — a worse
 * outcome than the seven screens, because at least those were consistent with themselves. Nothing
 * navigates now. Toggles flip, choices and text fields open under their own row, and the actions
 * that need the system — a folder picker, a file to write a backup into — open that system picker
 * over this screen.
 */
@Composable
fun NextSettingsScreen(
    snackbarHostState: NextSnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val updateCheck by viewModel.updateCheck.collectAsStateWithLifecycle()
    val imageCacheUsageBytes by viewModel.imageCacheUsageBytes.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: error("Could not open the selected file for reading")
                }
            }
        }

    LaunchedEffect(backupStatus) {
        val status = backupStatus ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(status.message())
        viewModel.clearBackupStatus()
    }
    // The row itself reports progress; the snackbar exists to carry the "Open" action, so it is
    // only worth raising once the check has actually finished.
    LaunchedEffect(updateCheck) {
        val available = updateCheck.availableRelease()
        val message = updateCheck.snackbarMessage() ?: return@LaunchedEffect
        val result =
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = available?.let { "Open" },
                withDismissAction = true,
            )
        if (result == NextSnackbarResult.ActionPerformed && available != null) {
            SafeExternalLinks.open(context, available.url)
        }
    }

    val cacheLabel = imageCacheUsageBytes.cacheSizeLabel()
    val model =
        remember(settings, updateCheck, cacheLabel, clearArmed) {
            buildSettings(settings, viewModel, updateCheck.rowValue(context), cacheLabel, clearArmed)
        }
    val groups = model.groups

    NextTheme {
        SettingsScreen(
            groups = groups,
            expandedId = expanded,
            onActivate = { item ->
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
                                    viewModel.clearLocalActivity()
                                    scope.launch { snackbarHostState.showSnackbar("Local activity cleared") }
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
            },
            onSelectOption = { item, index ->
                model.choose(item.id, index)
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

private fun Long.cacheSizeLabel(): String {
    if (this <= 0L) return "Empty · Clear"
    val megabytes = this.toDouble() / BYTES_PER_MB
    return if (megabytes < 1.0) "<1 MB · Clear" else "${megabytes.toInt()} MB · Clear"
}

private fun BackupStatus.message(): String =
    when (this) {
        BackupStatus.Exported -> "Backup saved"
        is BackupStatus.Imported ->
            "Restored ${summary.subscribedBoards} boards, ${summary.bookmarks} bookmarks and " +
                "${summary.savedSearches} saved searches" +
                if (summary.skippedUnknownProvider > 0) {
                    " (skipped ${summary.skippedUnknownProvider} from a provider this build doesn't support)"
                } else {
                    ""
                }
        is BackupStatus.Failed -> message
    }

private fun UpdateCheckState.availableRelease(): UpdateStatus.Available? =
    (this as? UpdateCheckState.Result)?.status as? UpdateStatus.Available

private fun UpdateCheckState.snackbarMessage(): String? =
    when (this) {
        UpdateCheckState.Idle, UpdateCheckState.Checking -> null
        is UpdateCheckState.Failed -> "Could not check for updates: $message"
        is UpdateCheckState.Result ->
            when (status) {
                UpdateStatus.UpToDate -> "Orbin is up to date"
                is UpdateStatus.Available -> "${status.name} is available"
            }
    }

private fun UpdateCheckState.rowValue(context: android.content.Context): String =
    when (this) {
        UpdateCheckState.Idle -> appVersionName(context).ifBlank { "Unknown build" }
        UpdateCheckState.Checking -> "Checking…"
        is UpdateCheckState.Failed -> "Check failed"
        is UpdateCheckState.Result ->
            when (status) {
                UpdateStatus.UpToDate -> "Up to date"
                is UpdateStatus.Available -> "${status.tag} available"
            }
    }
