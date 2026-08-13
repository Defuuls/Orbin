package com.orbin.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.designsystem.component.ModernListItem
import com.orbin.core.designsystem.component.ModernSmallTopAppBar
import com.orbin.core.model.DohProvider
import com.orbin.core.model.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Locking, local-data controls, the in-app updater, and DNS privacy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPrivacyScreen(
    onBack: () -> Unit,
    onOpenAdvanced: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val updateCheck by viewModel.updateCheck.collectAsStateWithLifecycle()
    val dnsFallbackActive by viewModel.dnsFallbackActive.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearLocalActivityDialog by remember { mutableStateOf(false) }
    val diagnosticsStatus by viewModel.diagnosticsStatus.collectAsStateWithLifecycle()

    // Writing the file is the caller's job, matching how backups are exported: the ViewModel
    // produces the text and never sees a SAF URI.
    val exportDiagnosticsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            viewModel.exportDiagnostics { report ->
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(report.toByteArray()) }
                        ?: error("Could not open the selected file")
                }
            }
        }

    LaunchedEffect(diagnosticsStatus) {
        val message =
            when (val status = diagnosticsStatus) {
                null -> return@LaunchedEffect
                DiagnosticsStatus.Exported -> "Crash details saved"
                DiagnosticsStatus.Empty -> "No crashes have been recorded"
                DiagnosticsStatus.Cleared -> "Crash details deleted"
                is DiagnosticsStatus.Failed -> status.message
            }
        snackbarHostState.showSnackbar(message = message, withDismissAction = true)
        viewModel.clearDiagnosticsStatus()
    }

    // The row itself shows progress; the snackbar exists to carry the "Open release" action, so it
    // is only worth raising once the check has actually finished.
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ModernSmallTopAppBar(
                title = "Privacy & Network",
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
            if (settings.internalUpdaterEnabled) {
                ModernListItem(
                    title = "Check for updates",
                    subtitle = updateCheck.rowSubtitle(appVersionName(context)),
                    // A null onClick makes the row unclickable, which is what a check in flight wants.
                    onClick =
                        if (updateCheck is UpdateCheckState.Checking) {
                            null
                        } else {
                            { viewModel.checkForUpdate(appVersionName(context)) }
                        },
                )
            }
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
                title = "Crash details",
                subtitle = "Save a copy of any recorded crashes. Nothing is sent anywhere on its own.",
                trailing = {
                    IconButton(onClick = { exportDiagnosticsLauncher.launch(DIAGNOSTICS_FILE_NAME) }) {
                        Icon(Icons.Filled.BugReport, contentDescription = "Save crash details")
                    }
                },
                onClick = { exportDiagnosticsLauncher.launch(DIAGNOSTICS_FILE_NAME) },
            )
            ModernListItem(
                title = "HTTPS only",
                subtitle = "Always enforced",
                trailing = { Switch(checked = true, onCheckedChange = null) },
            )
            ChoiceRow(
                label = "DNS over HTTPS",
                values = DohProvider.entries,
                selected = settings.dohProvider,
                text = { it.label },
                onChange = viewModel::setDohProvider,
            )
            SupportingNote(
                if (dnsFallbackActive) {
                    "This network is blocking ${settings.dohProvider.label}, so lookups are going " +
                        "through the system resolver and your DNS is not private right now. Try " +
                        "another resolver above, or another network."
                } else {
                    "Encrypted DNS is always on — choose which resolver answers your lookups. " +
                        "If a network blocks the one you pick, Orbin falls back to the system " +
                        "resolver and says so here rather than failing to load."
                },
            )
            ModernListItem(
                title = "Advanced",
                subtitle = "Custom user agent, timeouts, and certificate revocation checking",
                onClick = onOpenAdvanced,
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

private fun UpdateCheckState.rowSubtitle(currentVersionName: String): String =
    when (this) {
        UpdateCheckState.Idle -> "You are running ${currentVersionName.ifBlank { "an unknown build" }}"
        UpdateCheckState.Checking -> "Checking GitHub…"
        is UpdateCheckState.Failed -> "Check failed — tap to try again"
        is UpdateCheckState.Result ->
            when (status) {
                UpdateStatus.UpToDate -> "Up to date — tap to check again"
                is UpdateStatus.Available -> "${status.tag} is available on GitHub"
            }
    }

private const val DIAGNOSTICS_FILE_NAME = "orbin-diagnostics.txt"
