package com.orbin.uinext

/** The sync rows' ids, which Android's and iOS's settings screens both dispatch on. */
object SyncSettingIds {
    const val FOLDER = "syncFolder"
    const val USERNAME = "syncUsername"
    const val PASSWORD = "syncPassword"
    const val SYNC_NOW = "syncNow"
}

/**
 * The thread-sync rows, the same on Android and iOS: the WebDAV folder and its login, then a row
 * that syncs now and says how the last sync went. Built from plain values so each app can feed it
 * from the shared sync engine.
 */
fun syncSettingRows(
    folderUrl: String,
    username: String,
    hasPassword: Boolean,
    lastSyncedMillis: Long?,
    error: String?,
    nowMillis: Long,
): List<SettingItem> {
    val ready = folderUrl.startsWith("https://", ignoreCase = true) && username.isNotBlank() && hasPassword
    return listOf(
        SettingItem(
            id = SyncSettingIds.FOLDER,
            label = "Sync folder",
            value = folderUrl.ifBlank { "Off" },
            kind = SettingKind.TEXT,
            text = folderUrl,
            hint =
                "A WebDAV folder (Nextcloud, ownCloud, Synology…), https only. Use the same one on " +
                    "Android and iOS to share read threads, where you stopped reading, and watched threads.",
        ),
        SettingItem(
            id = SyncSettingIds.USERNAME,
            label = "Sync username",
            value = username.ifBlank { "Not set" },
            kind = SettingKind.TEXT,
            text = username,
        ),
        SettingItem(
            id = SyncSettingIds.PASSWORD,
            label = "Sync password",
            value = if (hasPassword) "Set" else "Not set",
            kind = SettingKind.TEXT,
            hint = "An app password, if your server offers them.",
            secret = true,
        ),
        SettingItem(
            id = SyncSettingIds.SYNC_NOW,
            label = "Sync now",
            value =
                when {
                    !ready -> "Set up above"
                    error != null -> "Retry"
                    else -> "Sync"
                },
            kind = SettingKind.ACTION,
            hint =
                when {
                    !ready -> null
                    error != null -> error
                    lastSyncedMillis == null -> "Not synced yet"
                    else -> "Last synced ${ago(nowMillis - lastSyncedMillis)}"
                },
        ),
    )
}

private fun ago(millis: Long): String {
    val minutes = millis / MILLIS_PER_MINUTE
    return when {
        minutes < 1 -> "just now"
        minutes < MINUTES_PER_HOUR -> "$minutes min ago"
        minutes < MINUTES_PER_DAY -> "${minutes / MINUTES_PER_HOUR} h ago"
        else -> "${minutes / MINUTES_PER_DAY} d ago"
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 1_440L
