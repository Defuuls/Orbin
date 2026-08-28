package com.orbin.feature.settings

/**
 * The running app's version name (e.g. "105-Himari"), or empty if it could not be read.
 *
 * All that is left of this file. It used to hold the row composables the seven category screens
 * shared — switches, choice chips, text fields, section headings — and every one of them went with
 * those screens: the settings list draws its own rows from `:ui-next`, out of one vocabulary rather
 * than a second set of Material components maintained beside it.
 */
internal fun appVersionName(context: android.content.Context): String =
    runCatching {
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            .orEmpty()
    }.getOrDefault("")
