package com.orbin.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.orbin.core.model.AppIconVariant
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        /**
         * Applies [variant], off the main thread. `setComponentEnabledSetting` is a synchronous
         * binder call into PackageManager that persists package state, and this runs on every
         * launch from a `LaunchedEffect`, so doing it on the main dispatcher costs startup frames.
         */
        suspend fun setIconVariant(variant: AppIconVariant) =
            withContext(Dispatchers.Default) {
                runCatching {
                    val pm = context.packageManager
                    if (pm.alreadyApplies(variant)) return@runCatching

                    // Enable the target before disabling the others. The reverse order leaves a
                    // window with no enabled alias, permanent if the process dies inside it.
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
                    // Alias toggling is unsupported on some OEM launchers; keep the current icon.
                    Log.w(TAG, "Failed to set icon variant to $variant", error)
                }
            }

        /**
         * True when the aliases already match [variant], so the writes can be skipped entirely.
         *
         * Reads are cheaper than writes here: each write persists package state and notifies the
         * launcher, and the overwhelmingly common case is a relaunch with the icon unchanged. A
         * fresh install reports [PackageManager.COMPONENT_ENABLED_STATE_DEFAULT] for the alias the
         * manifest enables, so the first run still writes and pins the state explicitly.
         */
        private fun PackageManager.alreadyApplies(variant: AppIconVariant): Boolean =
            AppIconVariant.entries.all { candidate ->
                val state = getComponentEnabledSetting(componentFor(candidate))
                if (candidate == variant) {
                    state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            }

        private fun componentFor(variant: AppIconVariant): ComponentName =
            ComponentName(context, AppIconAliases.qualifiedName(variant))

        private companion object {
            const val TAG = "AppIconManager"
        }
    }
