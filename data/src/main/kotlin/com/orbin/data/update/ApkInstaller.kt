package com.orbin.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * The Android side of installing an update: the checks only the package manager can make, and the
 * hand-off to the system installer, which asks the reader to confirm.
 */
class ApkInstaller
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** Where downloaded updates live. Shared with the FileProvider's `updates` path. */
        val updatesDir: File get() = File(context.cacheDir, UPDATES_DIR)

        /** Whether the reader has allowed Orbin to install apps. */
        fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

        fun openInstallPermissionSettings() {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        /**
         * Refuses an APK that is not a newer Orbin signed with the running build's key.
         *
         * Android would reject a key mismatch at install time anyway, but only after the reader
         * confirmed, with an unhelpful "App not installed". Checking first means a bad download
         * never reaches the installer at all.
         *
         * @throws UpdateVerificationException naming what is wrong.
         */
        fun verify(apk: File) {
            val manager = context.packageManager

            // The PackageInfoFlags overloads are API 33; these int ones cover minSdk 31 as well.
            @Suppress("DEPRECATION")
            val candidate = manager.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)

            @Suppress("DEPRECATION")
            val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val problem =
                when {
                    candidate == null -> "The download isn't a valid app"
                    candidate.packageName != installed.packageName -> "The download isn't Orbin"
                    candidate.longVersionCode <= installed.longVersionCode ->
                        "The download isn't newer than this version"
                    candidate.signers() != installed.signers() -> "The download isn't signed with Orbin's key"
                    else -> null
                }
            if (problem != null) throw UpdateVerificationException(problem)
        }

        /** Opens the system installer on [apk]; installing replaces, and so closes, the app. */
        fun install(apk: File) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_MIME_TYPE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        private fun PackageInfo.signers(): Set<String> =
            signingInfo
                ?.apkContentsSigners
                .orEmpty()
                .map { it.toCharsString() }
                .toSet()

        private companion object {
            const val UPDATES_DIR = "updates"
            const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        }
    }
