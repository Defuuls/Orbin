package com.orbin.uinext

object NearbySettingIds {
    /** The Settings row that opens the nearby devices screen. */
    const val OPEN = "nearbyOpen"
    const val ENABLED = "nearbyEnabled"
    const val STATUS = "nearbyStatus"
    const val THIS_DEVICE = "nearbyThisDevice"
    const val HOW_TO = "nearbyHowTo"
    const val ADD = "nearbyAdd"
    const val CODE = "nearbyCode"
    const val JOIN = "nearbyJoin"
    const val SHOW_QR = "nearbyShowQr"
    const val SCAN_QR = "nearbyScanQr"
    const val BLUETOOTH = "nearbyBluetooth"
    const val RELAY = "nearbyRelay"
    const val SYNC = "nearbySync"
    const val NO_DEVICES = "nearbyNoDevices"
    const val TROUBLESHOOT = "nearbyTroubleshoot"
    const val LOG = "nearbyLog"
    const val TRUST_PREFIX = "nearbyTrust:"
    const val FORGET_PREFIX = "nearbyForget:"
}

data class NearbyUiDevice(
    val id: String,
    val name: String,
    val trusted: Boolean = false,
    val lastSyncedMillis: Long? = null,
)

data class NearbyUiLogEntry(
    val timeMillis: Long,
    val text: String,
    val error: Boolean = false,
)

/** What the nearby devices screen shows; each platform maps its repository state into this. */
data class NearbyUi(
    val enabled: Boolean = false,
    val listening: Boolean = false,
    val busy: Boolean = false,
    val pairingCode: String? = null,
    val devices: List<NearbyUiDevice> = emptyList(),
    val message: String? = null,
    val messageIsError: Boolean = false,
    val step: String? = null,
    val deviceName: String? = null,
    val localAddresses: List<String> = emptyList(),
    val port: Int? = null,
    val bluetoothAvailable: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val relayAvailable: Boolean = false,
    val relayUrl: String? = null,
    val log: List<NearbyUiLogEntry> = emptyList(),
)

const val NEARBY_SCREEN_TITLE = "Nearby devices"

/** The single Settings row that leads to the nearby devices screen. */
fun nearbyEntryRow(ui: NearbyUi): SettingItem =
    SettingItem(
        id = NearbySettingIds.OPEN,
        label = NEARBY_SCREEN_TITLE,
        value =
            when {
                !ui.enabled -> "Off"
                ui.devices.isEmpty() -> "On"
                ui.devices.size == 1 -> "1 device"
                else -> "${ui.devices.size} devices"
            },
        kind = SettingKind.ACTION,
        hint = "Sync reading progress, bookmarks and watched threads directly with your other phones and tablets.",
    )

/**
 * The nearby devices screen, top to bottom: the switch and live status, this device's address,
 * a numbered guide with the pairing actions, the paired devices, other ways to connect,
 * troubleshooting and a copyable activity log. [qrCodes] adds the camera rows.
 */
fun nearbySyncGroups(
    ui: NearbyUi,
    nowMillis: Long,
    qrCodes: Boolean = true,
): List<Pair<String, List<SettingItem>>> =
    buildList {
        add("" to listOfNotNull(nearbyEnabledRow(ui.enabled), nearbyStatusRow(ui)))
        if (!ui.enabled) {
            add("How it works" to listOf(nearbyIntroRow()))
            return@buildList
        }
        add("This device" to listOf(nearbyThisDeviceRow(ui)))
        add("Add a device" to nearbyAddRows(ui, qrCodes))
        add("Your devices" to nearbyDeviceSection(ui, nowMillis))
        val extra =
            listOfNotNull(
                nearbyBluetoothRow(ui.bluetoothEnabled).takeIf { ui.bluetoothAvailable },
                nearbyRelayRow(ui.relayUrl).takeIf { ui.relayAvailable },
            )
        if (extra.isNotEmpty()) add("More ways to connect" to extra)
        add("Troubleshooting" to listOf(nearbyTroubleshootRow(ui)))
        add("Activity" to listOf(nearbyLogRow(ui.log, nowMillis)))
    }

private fun nearbyEnabledRow(enabled: Boolean) =
    SettingItem(
        id = NearbySettingIds.ENABLED,
        label = "Nearby device sync",
        value = if (enabled) "On" else "Off",
        hint = "Encrypted and direct between your devices. No account or server.",
    )

private fun nearbyIntroRow() =
    SettingItem(
        id = NearbySettingIds.HOW_TO,
        label = "Sync without an account",
        value = "",
        kind = SettingKind.INFO,
        hint =
            "Turn this on on both devices, then pair them once with a QR code. Afterwards they sync " +
                "whenever both have Orbin open on the same Wi-Fi. Images, passwords and settings stay " +
                "on each device.",
    )

/**
 * The current step with a progress bar while pairing or syncing, then the result, in red when it
 * failed. Hidden when there is nothing to say.
 */
private fun nearbyStatusRow(ui: NearbyUi): SettingItem? {
    if (!ui.enabled) return null
    val working = ui.busy && ui.step != null
    val waitingForJoin = ui.pairingCode != null
    val text =
        when {
            working -> ui.step
            ui.message != null -> ui.message
            waitingForJoin -> "Waiting for your other device to join with this code…"
            else -> return null
        }
    return SettingItem(
        id = NearbySettingIds.STATUS,
        label = "Status",
        value =
            when {
                working -> "Working…"
                ui.message != null && ui.messageIsError -> "Failed"
                ui.message != null -> "Done"
                else -> "Waiting"
            },
        kind = SettingKind.INFO,
        hint = text,
        progress = working || (waitingForJoin && ui.message == null),
        error = !working && ui.message != null && ui.messageIsError,
    )
}

private fun nearbyThisDeviceRow(ui: NearbyUi): SettingItem {
    val wifi = ui.localAddresses.filterNot { it.startsWith("127.") }
    val hint =
        when {
            !ui.listening -> "Starting… Keep Orbin open and unlocked."
            wifi.isEmpty() ->
                "No Wi-Fi address. Connect to Wi-Fi, or use Bluetooth or a relay below."
            else ->
                "Wi-Fi address ${wifi.joinToString()}${ui.port?.let { " (port $it)" }.orEmpty()}. " +
                    "Your other device's address should start with the same numbers " +
                    "(${wifi.first().substringBeforeLast('.')}.…) to reach this one over Wi-Fi."
        }
    return SettingItem(
        id = NearbySettingIds.THIS_DEVICE,
        label = ui.deviceName ?: "This device",
        value = if (ui.listening) "Ready" else "Not ready",
        kind = SettingKind.INFO,
        hint = hint,
    )
}

private fun nearbyAddRows(
    ui: NearbyUi,
    qrCodes: Boolean,
): List<SettingItem> =
    buildList {
        add(
            SettingItem(
                id = NearbySettingIds.HOW_TO,
                label = "How to pair",
                value = "",
                kind = SettingKind.INFO,
                hint =
                    if (qrCodes) {
                        "1. On one device, tap Show QR code.\n" +
                            "2. On the other, open Nearby devices and tap Scan QR code.\n" +
                            "3. Keep Orbin open on both until Status says Synced."
                    } else {
                        "1. On one device, tap Create code and copy it.\n" +
                            "2. On the other, open Nearby devices and paste it under Join with a code.\n" +
                            "3. Keep Orbin open on both until Status says Synced."
                    },
            ),
        )
        add(
            SettingItem(
                NearbySettingIds.ADD,
                "Add a device",
                if (qrCodes) "Show QR code" else "Create code",
                kind = SettingKind.ACTION,
                hint = "Makes a one-time code that expires in 10 minutes. Leaving Orbin cancels it.",
            ),
        )
        if (ui.pairingCode != null && qrCodes) {
            add(
                SettingItem(
                    id = NearbySettingIds.SHOW_QR,
                    label = "Pairing QR code",
                    value = "Show again",
                    kind = SettingKind.ACTION,
                ),
            )
        }
        if (ui.pairingCode != null) {
            add(
                SettingItem(
                    id = NearbySettingIds.CODE,
                    label = "Pairing code",
                    value = "Expires in 10 min",
                    kind = SettingKind.INFO,
                    text = ui.pairingCode,
                    hint = "Long-press to copy. Share it only with your own devices.",
                    copyable = true,
                ),
            )
        }
        if (qrCodes) {
            add(
                SettingItem(
                    id = NearbySettingIds.SCAN_QR,
                    label = "Join another device",
                    value = "Scan QR code",
                    kind = SettingKind.ACTION,
                    hint = "Point the camera at the QR code showing on your other device.",
                ),
            )
        }
        add(
            SettingItem(
                id = NearbySettingIds.JOIN,
                label = "Join with a code",
                value = "Paste code",
                kind = SettingKind.TEXT,
                hint = "Paste a pairing code copied from your other device.",
                secret = true,
            ),
        )
    }

private fun nearbyDeviceSection(
    ui: NearbyUi,
    nowMillis: Long,
): List<SettingItem> =
    if (ui.devices.isEmpty()) {
        listOf(
            SettingItem(
                id = NearbySettingIds.NO_DEVICES,
                label = "No devices yet",
                value = "",
                kind = SettingKind.INFO,
                hint = "Devices you pair appear here.",
            ),
        )
    } else {
        listOf(
            SettingItem(
                id = NearbySettingIds.SYNC,
                label = "Sync now",
                value = if (ui.busy) "Syncing…" else "Sync",
                kind = SettingKind.ACTION,
                hint = "Shares reading progress, bookmarks and watched threads with every device below.",
            ),
        ) + ui.devices.flatMap { nearbyDeviceRows(it, nowMillis) }
    }

private fun nearbyDeviceRows(
    device: NearbyUiDevice,
    nowMillis: Long,
): List<SettingItem> =
    listOf(
        SettingItem(
            id = NearbySettingIds.TRUST_PREFIX + device.id,
            label = device.name,
            value = if (device.trusted) "On" else "Off",
            hint =
                (device.lastSyncedMillis?.let { "Last synced ${ago(nowMillis - it)}. " } ?: "Not synced yet. ") +
                    "Automatic sync: turn on for this device on both devices to sync on the same Wi-Fi " +
                    "without tapping Sync now.",
        ),
        SettingItem(
            id = NearbySettingIds.FORGET_PREFIX + device.id,
            label = "Remove ${device.name}",
            value = "Remove",
            kind = SettingKind.ACTION,
            hint = "Revoke this device's access. Your reading history and bookmarks are kept.",
            destructive = true,
        ),
    )

private fun nearbyBluetoothRow(enabled: Boolean) =
    SettingItem(
        id = NearbySettingIds.BLUETOOTH,
        label = "Also use Bluetooth",
        value = if (enabled) "On" else "Off",
        hint =
            "Pair and sync when the devices aren't on the same Wi-Fi, within about 10 m. " +
                "Turn it on on both devices before pairing.",
    )

private fun nearbyRelayRow(url: String?) =
    SettingItem(
        id = NearbySettingIds.RELAY,
        label = "Relay server",
        value = url?.substringAfter("://") ?: "Not set",
        kind = SettingKind.TEXT,
        text = url.orEmpty(),
        hint =
            "Sync over the Internet or mobile data through a relay you host (https://…). " +
                "It only forwards encrypted data. Set it before Add a device; leave empty to turn off.",
    )

private fun nearbyTroubleshootRow(ui: NearbyUi) =
    SettingItem(
        id = NearbySettingIds.TROUBLESHOOT,
        label = "Can't connect?",
        value = "",
        kind = SettingKind.INFO,
        hint =
            "• Both devices on the same Wi-Fi network, not a guest network, and the first three numbers " +
                "of both addresses under This device match.\n" +
                "• iPhone/iPad: Settings → Privacy & Security → Local Network → Orbin on.\n" +
                "• Orbin open and unlocked on both devices the whole time; leaving it cancels a code.\n" +
                "• Turn off VPNs on both devices.\n" +
                "• Still stuck? Turn on Also use Bluetooth on both and pair again." +
                (if (ui.log.any { it.error }) "\n• The Activity log below shows what failed." else ""),
    )

private fun nearbyLogRow(
    log: List<NearbyUiLogEntry>,
    nowMillis: Long,
) = SettingItem(
    id = NearbySettingIds.LOG,
    label = "Recent activity",
    value = if (log.isEmpty()) "Nothing yet" else "${log.size} events",
    kind = SettingKind.INFO,
    hint = "Long-press to copy, for example to report a problem.",
    text =
        log.asReversed().joinToString("\n") { entry ->
            (if (entry.error) "✕ " else "• ") + "${ago(nowMillis - entry.timeMillis)}: ${entry.text}"
        },
    copyable = log.isNotEmpty(),
)
