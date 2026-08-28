package com.orbin.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.model.UpdateStatus
import com.orbin.uinext.NextTheme
import com.orbin.uinext.SettingItem
import com.orbin.uinext.SettingKind
import com.orbin.uinext.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BACKUP_FILE_NAME = "orbin-backup.json"
private const val DIAGNOSTICS_FILE_NAME = "orbin-diagnostics.txt"

/**
 * Every setting on one screen, and every one of them editable on it.
 *
 * The list used to hand eleven of its rows back to the category screens it replaced, which meant
 * pressing a setting could drop you into the interface this one was built to replace — a worse
 * outcome than the seven screens, because at least those were consistent with themselves. Nothing
 * navigates now. Toggles flip, choices and text fields open under their own row, and the actions
 * that need the system — a folder picker, a file to write a backup into — open that system picker
 * over this screen.
 *
 * [onRunSetup] is the one thing here that is genuinely somewhere else: the first-run wizard.
 */
@Composable
fun NextSettingsScreen(
    onOpenCommands: () -> Unit,
    onRunSetup: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    focusId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val diagnosticsStatus by viewModel.diagnosticsStatus.collectAsStateWithLifecycle()
    val updateCheck by viewModel.updateCheck.collectAsStateWithLifecycle()
    val dnsFallbackActive by viewModel.dnsFallbackActive.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            viewModel.setDownloadFolderUri(uri.toString())
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
    val diagnosticsExporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            viewModel.exportDiagnostics { report ->
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(report.toByteArray()) }
                        ?: error("Could not open the selected file")
                }
            }
        }

    LaunchedEffect(backupStatus) {
        val status = backupStatus ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(status.message())
        viewModel.clearBackupStatus()
    }
    LaunchedEffect(diagnosticsStatus) {
        val status = diagnosticsStatus ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = status.message(), withDismissAction = true)
        viewModel.clearDiagnosticsStatus()
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
        if (result == SnackbarResult.ActionPerformed && available != null) {
            uriHandler.openUri(available.url)
        }
    }

    val model =
        remember(settings, updateCheck, dnsFallbackActive) {
            buildSettings(settings, viewModel, updateCheck.rowValue(context), dnsFallbackActive)
        }

    NextTheme {
        SettingsScreen(
            groups = model.groups,
            subtitle = "${model.count} of them, in one list",
            expandedId = expanded,
            focusId = focusId,
            onSearch = onOpenCommands,
            onActivate = { item ->
                when (item.kind) {
                    SettingKind.TOGGLE -> model.toggle(item.id)
                    // Pressing an open row closes it again, so a row is never a one-way door.
                    SettingKind.CHOICE, SettingKind.TEXT ->
                        expanded = if (expanded == item.id) null else item.id
                    SettingKind.ACTION ->
                        dispatch(
                            item = item,
                            onFolder = { folderPicker.launch(null) },
                            onExport = { backupExporter.launch(BACKUP_FILE_NAME) },
                            onImport = { backupImporter.launch(arrayOf("application/json", "*/*")) },
                            onDiagnostics = { diagnosticsExporter.launch(DIAGNOSTICS_FILE_NAME) },
                            onClear = { confirmClear = true },
                            onCheckUpdates = {
                                if (updateCheck != UpdateCheckState.Checking) {
                                    viewModel.checkForUpdate(appVersionName(context))
                                }
                            },
                            onRunSetup = onRunSetup,
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

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear local activity?") },
            text = {
                Text("This deletes browsing history, recent searches, and download history stored on this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearLocalActivity()
                        confirmClear = false
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

/**
 * What an action row does, by id.
 *
 * A `when` over ids rather than a lambda on the row, because these need the launchers, which only
 * exist inside a composable — the registry builds the rows and this decides what pressing one does.
 */
@Suppress("LongParameterList")
private fun dispatch(
    item: SettingItem,
    onFolder: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDiagnostics: () -> Unit,
    onClear: () -> Unit,
    onCheckUpdates: () -> Unit,
    onRunSetup: () -> Unit,
) {
    when (item.id) {
        "downloadFolder" -> onFolder()
        "exportBackup" -> onExport()
        "importBackup" -> onImport()
        "crashDetails" -> onDiagnostics()
        "clearActivity" -> onClear()
        "checkUpdates" -> onCheckUpdates()
        "runSetup" -> onRunSetup()
    }
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

private fun DiagnosticsStatus.message(): String =
    when (this) {
        DiagnosticsStatus.Exported -> "Crash details saved"
        DiagnosticsStatus.Empty -> "No crashes have been recorded"
        DiagnosticsStatus.Cleared -> "Crash details deleted"
        is DiagnosticsStatus.Failed -> message
    }

/** The newer release this check found, or null when there is nothing to offer. */
private fun UpdateCheckState.availableRelease(): UpdateStatus.Available? =
    (this as? UpdateCheckState.Result)?.status as? UpdateStatus.Available

/** Null while the check is still in flight or has not been run: there is nothing to announce yet. */
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

/** What the check's own row reads as its value — the state of the last check, in a few words. */
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
