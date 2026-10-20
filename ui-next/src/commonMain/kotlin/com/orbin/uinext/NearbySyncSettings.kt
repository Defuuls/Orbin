package com.orbin.uinext

object NearbySettingIds {
    const val ENABLED = "nearbyEnabled"
    const val ADD = "nearbyAdd"
    const val CODE = "nearbyCode"
    const val JOIN = "nearbyJoin"
    const val SYNC = "nearbySync"
    const val FORGET_PREFIX = "nearbyForget:"
}

/** Nearby sync's shared rows. No network addresses or secrets are shown until pairing is requested. */
fun nearbySyncRows(
    enabled: Boolean,
    listening: Boolean,
    busy: Boolean,
    code: String?,
    devices: List<Pair<String, String>>,
    message: String?,
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
        add(SettingItem(NearbySettingIds.ADD, "Add a device", "Create code", kind = SettingKind.ACTION))
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
        add(
            SettingItem(
                id = NearbySettingIds.JOIN,
                label = "Join another device",
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
                    message
                        ?: (
                            "Shares reading progress, bookmarks and watched threads. " +
                                "Images, passwords and settings stay on this device."
                        ),
            ),
        )
        devices.forEach { (id, name) ->
            add(
                SettingItem(
                    id = NearbySettingIds.FORGET_PREFIX + id,
                    label = name,
                    value = "Remove device",
                    kind = SettingKind.ACTION,
                    hint = "Revoke this device's access. Local reading history and bookmarks are kept.",
                    destructive = true,
                ),
            )
        }
    }
