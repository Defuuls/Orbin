package com.orbin.feature.settings

import android.content.Context
import androidx.annotation.StringRes

/**
 * The settings screen's text, from its string resources: Android's on device, the same file
 * read directly in JVM tests, so the rows can be built and checked without a device.
 */
internal fun interface SettingsStrings {
    fun get(
        @StringRes id: Int,
        vararg args: Any,
    ): String
}

// The arguments are already an array; getString takes them as varargs, so they are spread once.
@Suppress("SpreadOperator")
internal fun Context.settingsStrings(): SettingsStrings = SettingsStrings { id, args -> getString(id, *args) }
