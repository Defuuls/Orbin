package com.orbin.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.orbin.core.model.AppIconVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Manages dynamic icon switching by enabling/disabling activity aliases. */
class IconSwitcher(private val context: Context) {
    private val packageManager = context.packageManager

    suspend fun switchIcon(variant: AppIconVariant) =
        withContext(Dispatchers.Default) {
            val enableComponentName = getComponentName(variant)
            val disableComponentNames = getOtherComponentNames(variant)

            try {
                packageManager.setComponentEnabledSetting(
                    enableComponentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )

                disableComponentNames.forEach { componentName ->
                    packageManager.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            } catch (e: Exception) {
                // Icon switching may fail on some devices; log but don't crash.
            }
        }

    private fun getComponentName(variant: AppIconVariant): ComponentName =
        ComponentName(
            context,
            when (variant) {
                AppIconVariant.DEFAULT -> ".OrbitalOrbIconAlias"
                AppIconVariant.NESTED_RINGS -> ".NestedRingsIconAlias"
                AppIconVariant.ABSTRACT_FLOW -> ".AbstractFlowIconAlias"
                AppIconVariant.MINIMALIST_ESSENCE -> ".MinimalistEssenceIconAlias"
                AppIconVariant.DUAL_GRADIENT -> ".DualGradientIconAlias"
            },
        )

    private fun getOtherComponentNames(variant: AppIconVariant): List<ComponentName> =
        AppIconVariant.entries
            .filter { it != variant }
            .map { getComponentName(it) }

    companion object {
        fun initialize(context: Context) = IconSwitcher(context)
    }
}
