package com.orbin.uinext

object NearbySettingIds {
    const val ENABLED = "nearbyEnabled"
    const val ADD = "nearbyAdd"
    const val CODE = "nearbyCode"
    const val JOIN = "nearbyJoin"
    const val SHOW_QR = "nearbyShowQr"
    const val SCAN_QR = "nearbyScanQr"
    const val BLUETOOTH = "nearbyBluetooth"
    const val RELAY = "nearbyRelay"
    const val SYNC = "nearbySync"
    const val STATUS = "nearbyStatus"
    const val TRUST_PREFIX = "nearbyTrust:"
    const val FORGET_PREFIX = "nearbyForget:"
}

/**
 * Nearby sync's shared rows. No network addresses or secrets are shown until pairing is requested.
 * [qrCodes] adds the show/scan QR rows on platforms with a camera scanner; [bluetoothAvailable]
 * adds the Bluetooth switch on platforms with a Bluetooth transport. Both default off, so a
 * platform without them keeps the copy/paste flow unchanged.
 */
@Suppress("LongParameterList")
fun nearbySyncRows(
    enabled: Boolean,
    listening: Boolean,
    busy: Boolean,
    code: String?,
    devices: List<Pair<String, String>>,
    message: String?,
    trustedIds: Set<String> = emptySet(),
    qrCodes: Boolean = false,
    bluetoothAvailable: Boolean = false,
    bluetoothEnabled: Boolean = false,
    relayAvailable: Boolean = false,
    relayUrl: String? = null,
    step: String? = null,
    messageIsError: Boolean = false,
): List<SettingItem> =
    buildList {
        add(
            SettingItem(
                id = NearbySettingIds.ENABLED,
                label = "Nearby device sync",
                value = if (enabled) "On" else "Off",
                hint =
                    "Encrypted, directly over the same Wi-Fi. Keep Orbin open and unlocked on both devices. " +
                        "No account or server.",
            ),
        )
        if (!enabled) return@buildList
        nearbyStatusRow(busy, step, message, messageIsError, code != null)?.let(::add)
        if (bluetoothAvailable) add(nearbyBluetoothRow(bluetoothEnabled))
        if (relayAvailable) add(nearbyRelayRow(relayUrl))
        add(
            SettingItem(
                NearbySettingIds.ADD,
                "Add a device",
                if (qrCodes) "Show QR code" else "Create code",
                kind = SettingKind.ACTION,
            ),
        )
        if (code != null && qrCodes) add(nearbyShowQrRow())
        if (code != null) {
            add(
                SettingItem(
                    id = NearbySettingIds.CODE,
                    label = "Pairing code",
                    value = "Expires in 10 min",
                    kind = SettingKind.INFO,
                    text = code,
                    hint =
                        "Long-press to copy, then paste it on your other device. " +
                            "Share it privately; it gives access to your activity.",
                    copyable = true,
                ),
            )
        }
        if (qrCodes) add(nearbyScanQrRow())
        add(
            SettingItem(
                id = NearbySettingIds.JOIN,
                label = if (qrCodes) "Join with a code instead" else "Join another device",
                value = "Paste code",
                kind = SettingKind.TEXT,
                hint = "Create a pairing code in Orbin on your other device, then paste it here.",
                secret = true,
            ),
        )
        add(
            SettingItem(
                id = NearbySettingIds.SYNC,
                label = "Sync now",
                value =
                    if (busy) {
                        "Syncing…"
                    } else if (listening) {
                        "Ready"
                    } else {
                        "Waiting"
                    },
                kind = SettingKind.ACTION,
                hint =
                    "Shares reading progress, bookmarks and watched threads. " +
                        "Images, passwords and settings stay on this device.",
            ),
        )
        devices.forEach { (id, name) -> addAll(nearbyDeviceRows(id, name, id in trustedIds)) }
    }

/**
 * A live status line at the top of the section: the current step with a progress bar while
 * pairing or syncing, then the result, in red when it failed. Hidden when there is nothing to say.
 */
private fun nearbyStatusRow(
    busy: Boolean,
    step: String?,
    message: String?,
    messageIsError: Boolean,
    waitingForJoin: Boolean,
): SettingItem? {
    val working = busy && step != null
    val text =
        when {
            working -> step
            message != null -> message
            waitingForJoin -> "Waiting for your other device to join with this code…"
            else -> return null
        }
    return SettingItem(
        id = NearbySettingIds.STATUS,
        label = "Status",
        value =
            when {
                working -> "Working…"
                message != null && messageIsError -> "Failed"
                message != null -> "Done"
                else -> "Waiting"
            },
        kind = SettingKind.INFO,
        hint = text,
        progress = working || (waitingForJoin && message == null),
        error = !working && message != null && messageIsError,
    )
}

private fun nearbyBluetoothRow(enabled: Boolean) =
    SettingItem(
        id = NearbySettingIds.BLUETOOTH,
        label = "Also use Bluetooth",
        value = if (enabled) "On" else "Off",
        hint =
            "Pair and sync when the devices aren't on the same Wi-Fi, within about 10 m. " +
                "Asks for Nearby devices access. Encrypted the same way as Wi-Fi.",
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

private fun nearbyShowQrRow() =
    SettingItem(
        id = NearbySettingIds.SHOW_QR,
        label = "Pairing QR code",
        value = "Show",
        kind = SettingKind.ACTION,
        hint = "Scan it with Orbin on your other device. Expires in 10 minutes.",
    )

private fun nearbyScanQrRow() =
    SettingItem(
        id = NearbySettingIds.SCAN_QR,
        label = "Join another device",
        value = "Scan QR code",
        kind = SettingKind.ACTION,
        hint = "Choose Add a device on your other device, then point this camera at its QR code.",
    )

private fun nearbyDeviceRows(
    id: String,
    name: String,
    trusted: Boolean,
): List<SettingItem> =
    listOf(
        SettingItem(
            id = NearbySettingIds.TRUST_PREFIX + id,
            label = "$name: trust for automatic sync",
            value = if (trusted) "On" else "Off",
            hint =
                "Enable on both devices to sync automatically when reachable on the same local network. " +
                    "Both apps must be open and unlocked.",
        ),
        SettingItem(
            id = NearbySettingIds.FORGET_PREFIX + id,
            label = name,
            value = "Remove device",
            kind = SettingKind.ACTION,
            hint = "Revoke this device's access. Local reading history and bookmarks are kept.",
            destructive = true,
        ),
    )
