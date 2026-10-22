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
import com.orbin.domain.repository.NearbySyncState
import com.orbin.uinext.NEARBY_SCREEN_TITLE
import com.orbin.uinext.NearbySettingIds
import com.orbin.uinext.NearbyUi
import com.orbin.uinext.NearbyUiDevice
import com.orbin.uinext.NearbyUiLogEntry
import com.orbin.uinext.NextSnackbarHostState
import com.orbin.uinext.NextTheme
import com.orbin.uinext.SettingsScreen
import com.orbin.uinext.nearbySyncGroups
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BLUETOOTH_PERMISSIONS =
    arrayOf(
        android.Manifest.permission.BLUETOOTH_SCAN,
        android.Manifest.permission.BLUETOOTH_ADVERTISE,
        android.Manifest.permission.BLUETOOTH_CONNECT,
    )

/** Refreshes the "ago" times in the activity log and device rows while the screen is open. */
private const val CLOCK_TICK_MS = 15_000L

/**
 * Nearby device sync on its own screen, opened from Settings: turning it on, this device's
 * address, pairing by QR code or pasted code, the paired devices, Bluetooth and relay options,
 * troubleshooting and the activity log.
 */
@Composable
fun NearbySyncScreen(
    snackbarHostState: NextSnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val nearbyState by viewModel.nearbyState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var showPairingQr by remember { mutableStateOf(false) }
    var pairingQrRequested by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(CLOCK_TICK_MS)
        }
    }
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
            showPairingQr = false
        } else if (pairingQrRequested) {
            pairingQrRequested = false
            showPairingQr = true
        }
    }
    val ui = nearbyState.toUi()

    NextTheme {
        SettingsScreen(
            groups = nearbySyncGroups(ui, nowMillis),
            title = NEARBY_SCREEN_TITLE,
            expandedId = expanded,
            onActivate = { item ->
                when {
                    item.id == NearbySettingIds.ENABLED -> viewModel.setNearbyEnabled(!nearbyState.enabled)
                    item.id == NearbySettingIds.ADD -> {
                        pairingQrRequested = true
                        viewModel.createNearbyCode()
                    }
                    item.id == NearbySettingIds.SHOW_QR -> showPairingQr = true
                    item.id == NearbySettingIds.SCAN_QR -> scanQr()
                    item.id == NearbySettingIds.BLUETOOTH ->
                        when {
                            nearbyState.bluetoothEnabled -> viewModel.setNearbyBluetooth(false)
                            BLUETOOTH_PERMISSIONS.all {
                                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                            } -> enableBluetooth()
                            else -> bluetoothPermissions.launch(BLUETOOTH_PERMISSIONS)
                        }
                    item.id == NearbySettingIds.JOIN || item.id == NearbySettingIds.RELAY ->
                        expanded = if (expanded == item.id) null else item.id
                    item.id == NearbySettingIds.SYNC -> viewModel.syncNearby()
                    item.id.startsWith(NearbySettingIds.TRUST_PREFIX) -> {
                        val id = item.id.removePrefix(NearbySettingIds.TRUST_PREFIX)
                        val device = nearbyState.devices.firstOrNull { it.id == id }
                        if (device != null) viewModel.setNearbyTrusted(id, !device.trusted)
                    }
                    item.id.startsWith(NearbySettingIds.FORGET_PREFIX) ->
                        viewModel.forgetNearby(item.id.removePrefix(NearbySettingIds.FORGET_PREFIX))
                }
            },
            onCommitText = { item, value ->
                when (item.id) {
                    NearbySettingIds.JOIN -> viewModel.joinNearby(value)
                    NearbySettingIds.RELAY -> viewModel.setNearbyRelay(value)
                }
                expanded = null
            },
            modifier = modifier,
        )
        if (showPairingQr) {
            nearbyState.pairingCode?.let { code ->
                QrCodeDialog(
                    title = "Scan on your other device",
                    payload = code,
                    note =
                        "In Orbin on the other device: Settings → Nearby devices → Scan QR code. " +
                            "Keep Orbin open here until it joins. Expires in 10 minutes.",
                    onDismiss = { showPairingQr = false },
                )
            }
        }
    }
}

internal fun NearbySyncState.toUi() =
    NearbyUi(
        enabled = enabled,
        listening = listening,
        busy = busy,
        pairingCode = pairingCode,
        devices = devices.map { NearbyUiDevice(it.id, it.name, it.trusted, it.lastSyncedMillis) },
        message = message,
        messageIsError = messageIsError,
        step = step,
        deviceName = deviceName,
        localAddresses = localAddresses,
        port = port,
        bluetoothAvailable = bluetoothAvailable,
        bluetoothEnabled = bluetoothEnabled,
        relayAvailable = relayAvailable,
        relayUrl = relayUrl,
        log = log.map { NearbyUiLogEntry(it.timeMillis, it.text, it.error) },
    )
