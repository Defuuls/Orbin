package com.orbin.domain.repository

/**
 * Remembers the newest build that has ever run on this install, so an older one can refuse to.
 *
 * Android already rejects installing a lower `versionCode` over an existing app, so the case this
 * covers is the one that gets past the package manager: a build installed with the downgrade flag
 * (`adb install -d`), or any install path that keeps app data while replacing the APK with an
 * older one. What it deliberately does not cover is uninstall-then-reinstall — that wipes app
 * data, and with it the high-water mark. Nothing an app can store in its own sandbox survives its
 * own uninstall, so this is a guard against rolling back, not a guarantee against ever running an
 * older build.
 */
interface VersionGuardRepository {
    /**
     * The highest `versionCode` recorded as having run here, or 0 when nothing has been recorded
     * yet (a fresh install, or an install that predates this guard).
     */
    fun highestVersionCodeSeen(): Int

    /**
     * True when the running build is older than one that has already run on this install, and so
     * must not start.
     */
    fun isDowngrade(): Boolean

    /**
     * Records the running build as having launched successfully, raising the high-water mark. Never
     * lowers it, so this is safe to call unconditionally.
     *
     * Call it only once a launch has actually got somewhere. Recording at process start would let a
     * build that crash-loops on startup raise the mark on its way down and lock the user out of the
     * working build they came from — turning a bad release into an unrecoverable one.
     */
    fun recordSuccessfulLaunch()

    /** The running build's `versionCode`, for telling the user which build they are on. */
    fun currentVersionCode(): Int
}
