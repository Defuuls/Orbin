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
        /** Release page to open, rather than downloading the APK in-app. */
        val url: String,
    ) : UpdateStatus
}
