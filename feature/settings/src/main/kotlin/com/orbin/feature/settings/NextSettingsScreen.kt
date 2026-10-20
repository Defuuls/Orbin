@file:Suppress("CyclomaticComplexMethod")

package com.orbin.feature.settings

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
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
import com.orbin.core.model.UpdateStatus
import com.orbin.core.ui.device.rememberFormFactor
import com.orbin.uinext.NearbySettingIds
import com.orbin.uinext.NextSnackbarHostState
import com.orbin.uinext.NextTheme
import com.orbin.uinext.SettingItem
import com.orbin.uinext.SettingKind
import com.orbin.uinext.SettingsScreen
import com.orbin.uinext.SyncSettingIds
import com.orbin.uinext.nearbySyncRows
import com.orbin.uinext.syncSetupQrRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CLEAR_ARMED_MS = 4_000L
private const val BACKUP_FILE_NAME = "orbin-backup.json"
private const val BYTES_PER_MB = 1024L * 1024L
private val BLUETOOTH_PERMISSIONS =
    arrayOf(
        android.Manifest.permission.BLUETOOTH_SCAN,
        android.Manifest.permission.BLUETOOTH_ADVERTISE,
        android.Manifest.permission.BLUETOOTH_CONNECT,
    )

/** Which QR code the screen is showing, if any. */
private sealed interface ShownQr {
    data object Pairing : ShownQr

    data class SyncSetup(
        val code: String,
    ) : ShownQr
}

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
    val checkUpdatesOnLaunch by viewModel.checkUpdatesOnLaunch.collectAsStateWithLifecycle()
    val formFactor = rememberFormFactor()
    val imageCacheUsageBytes by viewModel.imageCacheUsageBytes.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val nearbyState by viewModel.nearbyState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var clearArmed by remember { mutableStateOf(false) }
    var shownQr by remember { mutableStateOf<ShownQr?>(null) }
    var pairingQrRequested by remember { mutableStateOf(false) }
    val scanQr =
        rememberQrScanner("Point at the QR code on your other device") { code ->
            viewModel.applyScannedCode(code) { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
        }
    // Turning Bluetooth on happens after the permission prompt and, if needed, the system's own
    // "turn on Bluetooth" dialog; each step pauses this screen, so refresh before enabling.
    val bluetoothEnabler =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refreshNearbyBluetooth()
            viewModel.setNearbyBluetooth(true)
        }
    val enableBluetooth = {
        viewModel.refreshNearbyBluetooth()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter?.isEnabled == true) {
            viewModel.setNearbyBluetooth(true)
        } else {
            bluetoothEnabler.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }
    val bluetoothPermissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.isNotEmpty() && grants.values.all { it }) {
                enableBluetooth()
            } else {
                scope.launch { snackbarHostState.showSnackbar("Bluetooth sync needs Nearby devices access") }
            }
        }
    // A freshly created pairing code opens straight into its QR code.
    LaunchedEffect(nearbyState.pairingCode) {
        if (nearbyState.pairingCode == null) {
            if (shownQr == ShownQr.Pairing) shownQr = null
        } else if (pairingQrRequested) {
            pairingQrRequested = false
            shownQr = ShownQr.Pairing
        }
    }
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
    // The row itself reports progress; the snackbar says how a finished check went. A newer
    // release says so in the app-wide update dialog instead, which can install it.
    LaunchedEffect(updateCheck) {
        val message = updateCheck.snackbarMessage() ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message, withDismissAction = true)
    }

    val cacheLabel = imageCacheUsageBytes.cacheSizeLabel()
    val model =
        remember(settings, updateCheck, cacheLabel, clearArmed, checkUpdatesOnLaunch, formFactor, syncState) {
            buildSettings(
                settings,
                viewModel,
                updateCheck.rowValue(context),
                cacheLabel,
                clearArmed,
                checkUpdatesOnLaunch,
                formFactor,
                syncState,
                System.currentTimeMillis(),
            )
        }
    val groups =
        model.groups + (
            "Nearby devices" to
                nearbySyncRows(
                    nearbyState.enabled,
                    nearbyState.listening,
                    nearbyState.busy,
                    nearbyState.pairingCode,
                    nearbyState.devices.map { it.id to it.name },
                    nearbyState.message,
                    trustedIds =
                        nearbyState.devices
                            .filter { it.trusted }
                            .map { it.id }
                            .toSet(),
                    qrCodes = true,
                    bluetoothAvailable = nearbyState.bluetoothAvailable,
                    bluetoothEnabled = nearbyState.bluetoothEnabled,
                )
        ) + ("Sync setup by QR code" to syncSetupQrRows(syncState.isConfigured()))

    NextTheme {
        SettingsScreen(
            groups = groups,
            expandedId = expanded,
            onActivate = { item ->
                if (item.id.startsWith("nearby")) {
                    when {
                        item.id == NearbySettingIds.ENABLED -> viewModel.setNearbyEnabled(!nearbyState.enabled)
                        item.id == NearbySettingIds.ADD -> {
                            pairingQrRequested = true
                            viewModel.createNearbyCode()
                        }
                        item.id == NearbySettingIds.SHOW_QR -> shownQr = ShownQr.Pairing
                        item.id == NearbySettingIds.SCAN_QR -> scanQr()
                        item.id == NearbySettingIds.BLUETOOTH ->
                            when {
                                nearbyState.bluetoothEnabled -> viewModel.setNearbyBluetooth(false)
                                BLUETOOTH_PERMISSIONS.all {
                                    context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                                } -> enableBluetooth()
                                else -> bluetoothPermissions.launch(BLUETOOTH_PERMISSIONS)
                            }
                        item.id == NearbySettingIds.JOIN -> expanded = if (expanded == item.id) null else item.id
                        item.id == NearbySettingIds.SYNC -> viewModel.syncNearby()
                        item.id.startsWith(NearbySettingIds.TRUST_PREFIX) -> {
                            val id = item.id.removePrefix(NearbySettingIds.TRUST_PREFIX)
                            val device = nearbyState.devices.firstOrNull { it.id == id }
                            if (device != null) viewModel.setNearbyTrusted(id, !device.trusted)
                        }
                        item.id.startsWith(NearbySettingIds.FORGET_PREFIX) ->
                            viewModel.forgetNearby(item.id.removePrefix(NearbySettingIds.FORGET_PREFIX))
                    }
                } else {
                    when (item.kind) {
                        SettingKind.TOGGLE -> model.toggle(item.id)
                        SettingKind.CHOICE, SettingKind.TEXT ->
                            expanded = if (expanded == item.id) null else item.id
                        SettingKind.ACTION ->
                            if (item.id == SyncSettingIds.SYNC_NOW) {
                                viewModel.syncNow()
                            } else if (item.id == SyncSettingIds.SCAN_SETUP_QR) {
                                scanQr()
                            } else if (item.id == SyncSettingIds.SHOW_SETUP_QR) {
                                scope.launch {
                                    val code = viewModel.syncSetupCode()
                                    if (code == null) {
                                        snackbarHostState.showSnackbar("Set up WebDAV sync on this device first")
                                    } else {
                                        shownQr = ShownQr.SyncSetup(code)
                                    }
                                }
                            } else {
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
                            }
                        SettingKind.INFO -> Unit
                    }
                }
            },
            onSelectOption = { item, index ->
                model.choose(item.id, index)
                expanded = null
            },
            onCommitText = { item, value ->
                if (item.id == NearbySettingIds.JOIN) viewModel.joinNearby(value) else model.commit(item.id, value)
                expanded = null
            },
            modifier = modifier,
        )
        when (val qr = shownQr) {
            ShownQr.Pairing ->
                nearbyState.pairingCode?.let { code ->
                    QrCodeDialog(
                        title = "Scan on your other device",
                        payload = code,
                        note =
                            "In Orbin on the other device: Settings → Nearby devices → Join another device. " +
                                "Expires in 10 minutes. Only share it with your own devices.",
                        onDismiss = { shownQr = null },
                    )
                }
            is ShownQr.SyncSetup ->
                QrCodeDialog(
                    title = "Set up sync on another device",
                    payload = qr.code,
                    note =
                        "In Orbin on the other device: Settings → Copy setup from another device. " +
                            "This contains your WebDAV password.",
                    onDismiss = { shownQr = null },
                )
            null -> Unit
        }
    }
}

private fun com.orbin.domain.repository.ThreadSyncState.isConfigured(): Boolean =
    folderUrl.startsWith("https://", ignoreCase = true) && username.isNotBlank() && hasPassword

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

private fun UpdateCheckState.snackbarMessage(): String? =
    when (this) {
        UpdateCheckState.Idle, UpdateCheckState.Checking -> null
        is UpdateCheckState.Failed -> "Could not check for updates: $message"
        is UpdateCheckState.Result ->
            when (status) {
                UpdateStatus.UpToDate -> "Orbin is up to date"
                // Shown by the update dialog rather than a snackbar.
                is UpdateStatus.Available -> null
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
