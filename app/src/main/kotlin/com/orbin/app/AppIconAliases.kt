package com.orbin.app

import com.orbin.core.model.AppIconVariant

/**
 * Maps each [AppIconVariant] to the manifest activity alias carrying its launcher icon.
 *
 * Deliberately free of Android types so the manifest invariants can be asserted from a plain JVM
 * unit test — see `AppIconAliasManifestTest`. Those invariants are not cosmetic: the package must
 * always have exactly one enabled launcher alias, and an alias the code never manages stays
 * enabled forever. Violating either shipped an app that could not be opened from the launcher.
 */
internal object AppIconAliases {
    const val PACKAGE = "com.orbin.app"

    /**
     * Unqualified alias name for [variant]. Exhaustive by design: adding a variant without an
     * alias is a compile error rather than a component that silently goes unmanaged.
     */
    fun simpleName(variant: AppIconVariant): String =
        when (variant) {
            AppIconVariant.DEFAULT -> "OrbitalOrbIconAlias"
            AppIconVariant.NESTED_RINGS -> "NestedRingsIconAlias"
            AppIconVariant.ABSTRACT_FLOW -> "AbstractFlowIconAlias"
            AppIconVariant.MINIMALIST_ESSENCE -> "MinimalistEssenceIconAlias"
            AppIconVariant.DUAL_GRADIENT -> "DualGradientIconAlias"
        }

    /** Fully qualified component name for [variant], as `setComponentEnabledSetting` expects. */
    fun qualifiedName(variant: AppIconVariant): String = "$PACKAGE.${simpleName(variant)}"
}
