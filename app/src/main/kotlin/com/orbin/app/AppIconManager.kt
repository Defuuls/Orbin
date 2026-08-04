package com.orbin.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.orbin.core.model.AppIconVariant
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Switches the launcher icon by toggling the activity aliases declared in the manifest.
 *
 * The package must always have exactly one enabled launcher alias. Zero leaves the app with no
 * launcher entry at all — a pinned shortcut to a disabled component opens App Info instead of the
 * app, and the only recovery is a reinstall. More than one shows the app twice in the launcher.
 */
class AppIconManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun setIconVariant(variant: AppIconVariant) {
            runCatching {
                val pm = context.packageManager

                // Enable the target before disabling the others. The reverse order leaves a window
                // with no enabled launcher alias, which becomes permanent if the process dies in it.
                pm.setComponentEnabledSetting(
                    componentFor(variant),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )

                AppIconVariant.entries
                    .filter { it != variant }
                    .forEach { other ->
                        pm.setComponentEnabledSetting(
                            componentFor(other),
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP,
                        )
                    }
            }.onFailure { error ->
                // Alias toggling is unsupported on some OEM launchers; stay on the current icon.
                Log.w(TAG, "Failed to set icon variant to $variant", error)
            }
        }

        private fun componentFor(variant: AppIconVariant): ComponentName =
            ComponentName(context, AppIconAliases.qualifiedName(variant))

        private companion object {
            const val TAG = "AppIconManager"
        }
    }
