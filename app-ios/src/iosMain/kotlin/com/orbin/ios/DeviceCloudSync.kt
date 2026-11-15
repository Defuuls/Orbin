package com.orbin.ios

import kotlinx.coroutines.CoroutineScope
import platform.Foundation.NSUserDefaults

/** Installation-local opt-in and account identity are intentionally absent from portable backups. */
internal fun deviceCloudSync(
    local: CloudSyncLocalStore,
    scope: CoroutineScope,
    isAuthorized: () -> Boolean,
): CloudSyncController {
    val preferences = NSUserDefaults.standardUserDefaults
    return CloudSyncController(
        local = local,
        transport = DeviceCloudSyncTransport(),
        scope = scope,
        isAuthorized = isAuthorized,
        readEnabled = { preferences.boolForKey("orbin.cloud.enabled") },
        writeEnabled = { preferences.setBool(it, forKey = "orbin.cloud.enabled") },
        readAccount = { preferences.stringForKey("orbin.cloud.account") },
        writeAccount = { account ->
            if (account == null) {
                preferences.removeObjectForKey("orbin.cloud.account")
            } else {
                preferences.setObject(account, forKey = "orbin.cloud.account")
            }
        },
    )
}
