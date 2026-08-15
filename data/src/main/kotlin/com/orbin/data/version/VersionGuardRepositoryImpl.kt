package com.orbin.data.version

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.orbin.domain.repository.VersionGuardRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the high-water mark in its own [android.content.SharedPreferences] file rather than in
 * settings DataStore.
 *
 * Two reasons. It is read on the startup path before anything else is trusted, and
 * SharedPreferences answers synchronously where DataStore would need a blocking coroutine — the
 * same trade `DiagnosticsRepositoryImpl` makes for its crash-loop counter. And it is not a setting:
 * keeping it out of [com.orbin.core.model.AppSettings] keeps it out of backup export and restore,
 * so restoring an old backup cannot quietly roll the mark backwards.
 */
@Singleton
class VersionGuardRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : VersionGuardRepository {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun highestVersionCodeSeen(): Int = prefs.getInt(KEY_HIGHEST_VERSION_CODE, 0)

        override fun isDowngrade(): Boolean {
            val current = currentVersionCode()
            // An unreadable version code reads as 0, which would make every launch look like a
            // downgrade and brick the app. Refusing to block is the only safe way to be wrong here.
            if (current <= 0) return false
            return current < highestVersionCodeSeen()
        }

        override fun recordSuccessfulLaunch() {
            val current = currentVersionCode()
            if (current <= 0) return
            // max(), not assignment: this must never lower the mark, whatever it is called from.
            if (current > highestVersionCodeSeen()) {
                prefs.edit().putInt(KEY_HIGHEST_VERSION_CODE, current).apply()
            }
        }

        /**
         * The only documented failure is `NameNotFoundException`, which cannot apply to the package
         * doing the asking. Swallowed to 0 ("unknown") anyway, because a guard that can throw on the
         * startup path is worse than a guard that declines to act.
         */
        override fun currentVersionCode(): Int =
            runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                // longVersionCode is the real field since API 28; the compat helper folds the
                // deprecated 32-bit one in for older devices. Orbin's codes are small, so the
                // narrowing to Int is safe and keeps the stored value a plain preference int.
                PackageInfoCompat.getLongVersionCode(info).toInt()
            }.getOrDefault(0)

        private companion object {
            const val PREFS_NAME = "orbin_version_guard"
            const val KEY_HIGHEST_VERSION_CODE = "highest_version_code"
        }
    }
