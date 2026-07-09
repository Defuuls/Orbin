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
            try {
                val aliasNames =
                    mapOf(
                        AppIconVariant.DEFAULT to "com.orbin.app.DefaultIconAlias",
                        AppIconVariant.MINIMALIST to "com.orbin.app.MinimalistIconAlias",
                        AppIconVariant.GRADIENT to "com.orbin.app.GradientIconAlias",
                        AppIconVariant.NEON to "com.orbin.app.NeonIconAlias",
                        AppIconVariant.RETRO to "com.orbin.app.RetroIconAlias",
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
            } catch (e: Exception) {
                android.util.Log.e("AppIconManager", "Failed to set icon variant: ${e.message}", e)
            }
        }
    }
