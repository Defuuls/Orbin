package com.orbin.core.model

/** Credentials, app lock, temporary reveals and the local storage destination never leave this install. */
fun AppSettings.portableExport(): AppSettings =
    copy(
        biometricLockEnabled = false,
        privateSavedFiles = false,
        nsfwSessionRevealed = false,
    )
