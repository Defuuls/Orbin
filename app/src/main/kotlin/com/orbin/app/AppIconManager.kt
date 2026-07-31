package com.orbin.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.orbin.core.model.AppIconVariant
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Manages app icon variant switching via activity aliases. */
class AppIconManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun setIconVariant(variant: AppIconVariant) {
            runCatching {
                val aliasNames =
                    mapOf(
                        AppIconVariant.DEFAULT to "com.orbin.app.OrbitalOrbIconAlias",
                        AppIconVariant.NESTED_RINGS to "com.orbin.app.NestedRingsIconAlias",
                        AppIconVariant.ABSTRACT_FLOW to "com.orbin.app.AbstractFlowIconAlias",
                        AppIconVariant.MINIMALIST_ESSENCE to "com.orbin.app.MinimalistEssenceIconAlias",
                        AppIconVariant.DUAL_GRADIENT to "com.orbin.app.DualGradientIconAlias",
                    )

                val pm = context.packageManager
                aliasNames.forEach { (iconVariant, aliasName) ->
                    val componentName = ComponentName(context, aliasName)
                    val state =
                        if (iconVariant == variant) {
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        } else {
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        }
                    pm.setComponentEnabledSetting(componentName, state, PackageManager.DONT_KILL_APP)
                }
            }.onFailure { e ->
                android.util.Log.e("AppIconManager", "Failed to set icon variant: ${e.message}", e)
            }
        }
    }
