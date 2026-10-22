package com.orbin.ios

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.orbin.domain.repository.NearbySyncRepository
import com.orbin.domain.repository.NearbySyncState
import com.orbin.uinext.NEARBY_SCREEN_TITLE
import com.orbin.uinext.NearbySettingIds
import com.orbin.uinext.NearbyUi
import com.orbin.uinext.NearbyUiDevice
import com.orbin.uinext.NearbyUiLogEntry
import com.orbin.uinext.SettingsScreen
import com.orbin.uinext.nearbySyncGroups
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Refreshes the "ago" times in the activity log and device rows while the screen is open. */
private const val CLOCK_TICK_MS = 15_000L

/**
 * Nearby device sync on its own screen, the same rows as Android's: turning it on, this device's
 * address, pairing by QR code or pasted code, the paired devices, troubleshooting and the log.
 */
@Composable
internal fun NearbySyncDestination(nearby: NearbySyncRepository?) {
    val state by remember(nearby) { nearby?.state ?: flowOf(NearbySyncState()) }.collectAsState(NearbySyncState())
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf<String?>(null) }
    var showPairingQr by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var nowMillis by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = Clock.System.now().toEpochMilliseconds()
            delay(CLOCK_TICK_MS)
        }
    }
    LaunchedEffect(state.pairingCode) { if (state.pairingCode == null) showPairingQr = false }
    val scan = {
        scanQrCode(
            onResult = { raw ->
                val code = raw.trim()
                if (code.startsWith(NEARBY_PAIRING_PREFIX)) {
                    scope.launch { nearby?.join(code) }
                } else {
                    notice = "That isn't an Orbin nearby pairing code. On the other device, open Nearby devices " +
                        "and tap Show QR code."
                }
            },
            onFailure = { notice = it },
        )
    }
    SettingsScreen(
        groups = nearbySyncGroups(state.toUi(), nowMillis),
        title = NEARBY_SCREEN_TITLE,
        expandedId = expanded,
        showRail = false,
        onActivate = { item ->
            Haptics.light()
            when {
                item.id == NearbySettingIds.ENABLED -> scope.launch { nearby?.setEnabled(!state.enabled) }
                item.id == NearbySettingIds.ADD -> {
                    showPairingQr = true
                    scope.launch { nearby?.createPairingCode() }
                }
                item.id == NearbySettingIds.SHOW_QR -> showPairingQr = true
                item.id == NearbySettingIds.SCAN_QR -> scan()
                item.id == NearbySettingIds.BLUETOOTH ->
                    scope.launch { nearby?.setBluetoothEnabled(!state.bluetoothEnabled) }
                item.id == NearbySettingIds.JOIN || item.id == NearbySettingIds.RELAY ->
                    expanded = if (expanded == item.id) null else item.id
                item.id == NearbySettingIds.SYNC -> scope.launch { nearby?.syncNow() }
                item.id.startsWith(NearbySettingIds.TRUST_PREFIX) -> {
                    val id = item.id.removePrefix(NearbySettingIds.TRUST_PREFIX)
                    val device = state.devices.firstOrNull { it.id == id }
                    if (device != null) scope.launch { nearby?.setTrusted(id, !device.trusted) }
                }
                item.id.startsWith(NearbySettingIds.FORGET_PREFIX) ->
                    scope.launch { nearby?.forget(item.id.removePrefix(NearbySettingIds.FORGET_PREFIX)) }
            }
        },
        onCommitText = { item, text ->
            Haptics.light()
            expanded = null
            when (item.id) {
                NearbySettingIds.JOIN -> scope.launch { nearby?.join(text) }
                NearbySettingIds.RELAY -> scope.launch { nearby?.setRelayUrl(text) }
            }
        },
    )
    if (showPairingQr) {
        state.pairingCode?.let { code ->
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
    notice?.let { text ->
        AlertDialog(
            onDismissRequest = { notice = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("OK") } },
        )
    }
}

private const val NEARBY_PAIRING_PREFIX = "orbin-nearby1:"

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
