package com.orbin.data.crypto

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Something about the reader's on-device data that they should hear about once. */
enum class LocalDataNotice {
    /**
     * The saved database key could not be read back (the Keystore lost it), so bookmarks, history
     * and downloads were reset. A backup is the only way to get them back.
     */
    RESET,

    /**
     * The Keystore would not save a database key, so this session keeps everything in memory and
     * leaves what is on disk alone. Nothing changed on disk; the next start tries again.
     */
    NOT_SAVED,
}

/**
 * Process-wide, like the passphrase it reports on: the database is opened lazily from Hilt, long
 * before and far away from any UI, and the UI shows whatever was reported until the reader closes it.
 */
object LocalDataNotices {
    private val _current = MutableStateFlow<LocalDataNotice?>(null)

    val current: StateFlow<LocalDataNotice?> = _current.asStateFlow()

    internal fun report(notice: LocalDataNotice) {
        _current.value = notice
    }

    fun acknowledge() {
        _current.value = null
    }
}
