package com.orbin.core.model

/** Outcome of checking GitHub Releases for a newer build than the one running. */
sealed interface UpdateStatus {
    /** The running build is the newest published release. */
    data object UpToDate : UpdateStatus

    data class Available(
        /** Release tag, e.g. `v62-Aldebaran`. */
        val tag: String,
        /** Human-readable release name, falling back to [tag] when the release has none. */
        val name: String,
        /** Release page, for when the APK cannot be installed in-app. */
        val url: String,
        /** The release's signed APK, or null when the release carries none. */
        val apkUrl: String? = null,
        /** The APK's published `.sha256` file; an APK without one is never installed. */
        val checksumUrl: String? = null,
    ) : UpdateStatus {
        /** True when the app can download, verify and install this release itself. */
        val installable: Boolean get() = apkUrl != null && checksumUrl != null
    }
}

/**
 * Where an in-app update stands, from the offer through to handing the APK to the system
 * installer. There is no "installed" state: the installer replaces the running app.
 */
sealed interface AppUpdateState {
    /** Nothing to show. */
    data object Idle : AppUpdateState

    /** A newer release is available and has not been dismissed. */
    data class Available(
        val release: UpdateStatus.Available,
    ) : AppUpdateState

    /** The APK is downloading; [progress] is 0..1, or null while the size is unknown. */
    data class Downloading(
        val release: UpdateStatus.Available,
        val progress: Float?,
    ) : AppUpdateState

    /**
     * The APK is downloaded and verified, but Android has not yet been told Orbin may install
     * apps. The reader grants it in system settings, then installs from here.
     */
    data class NeedsInstallPermission(
        val release: UpdateStatus.Available,
    ) : AppUpdateState

    /** The download or its verification failed; [message] says why. */
    data class Failed(
        val release: UpdateStatus.Available,
        val message: String,
    ) : AppUpdateState
}
