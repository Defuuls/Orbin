package com.orbin.data.crypto

import android.content.Context

/** Room and the media cache share a key epoch, but each physical file is reset independently. */
internal fun DatabasePassphrase.Resolved.prepareDatabase(
    context: Context,
    name: String,
) {
    val file = context.getDatabasePath(name)
    prepareDatabaseOnce(file.canonicalPath) {
        check(context.deleteDatabase(name) || !file.exists()) { "Could not reset encrypted database" }
    }
}
