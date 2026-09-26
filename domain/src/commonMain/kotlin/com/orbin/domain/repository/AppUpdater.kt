package com.orbin.domain.repository

import com.orbin.core.model.AppUpdateState
import com.orbin.core.model.UpdateStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Offers a newer release and installs it in-app: download the APK, check it against the
 * release's published SHA-256 and signing key, then hand it to the system installer.
 *
 * One instance for the whole app, so the launch check and Settings' manual check share the same
 * dialog and the same download.
 */
interface AppUpdater {
    val state: StateFlow<AppUpdateState>

    /** Whether the app looks for a new release when it opens. */
    val checkOnLaunch: StateFlow<Boolean>

    fun setCheckOnLaunch(enabled: Boolean)

    /**
     * The launch check: quiet, at most once per interval, and silent on failure or when the newest
     * release is one the reader already dismissed.
     *
     * @param currentVersionName the running build's `versionName`, e.g. `149-Orange`.
     */
    suspend fun checkOnLaunch(currentVersionName: String)

    /** Shows [release] as available, as a manual check in Settings does. */
    fun offer(release: UpdateStatus.Available)

    /** Downloads, verifies and installs [release]. */
    fun update(release: UpdateStatus.Available)

    /** Stops a download in progress. */
    fun cancel()

    /** Closes the dialog; an [AppUpdateState.Available] release is not offered again on launch. */
    fun dismiss()

    /** Opens the system screen where Orbin is allowed to install apps. */
    fun openInstallPermissionSettings()

    /** Installs the verified APK once the permission is granted. */
    fun installDownloaded()
}
