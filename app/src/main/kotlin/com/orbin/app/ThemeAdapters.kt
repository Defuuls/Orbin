package com.orbin.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.orbin.core.designsystem.theme.ColorSchemeVariant
import com.orbin.core.designsystem.theme.OrbinTheme
import com.orbin.core.designsystem.theme.ThemeMode
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.ColorTheme

/**
 * Maps persisted theme settings onto the two theme layers the activity hosts.
 *
 * Kept out of [MainActivity] so the activity file stays the splash / lock / permission host
 * rather than also owning enum adapters.
 *
 * [isDark] is the same choice as [toDesignSystem], resolved to a plain boolean for `:ui-next`,
 * which has one dark palette and one light one rather than a mode. SYSTEM is answered here rather
 * than left to NextTheme's own default, so both theme layers read the setting in one composition.
 */
@Composable
internal fun AppThemeMode.isDark(): Boolean =
    when (this) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

internal fun AppThemeMode.toDesignSystem(): ThemeMode =
    when (this) {
        AppThemeMode.SYSTEM -> ThemeMode.SYSTEM
        AppThemeMode.LIGHT -> ThemeMode.LIGHT
        AppThemeMode.DARK -> ThemeMode.DARK
    }

// The two enums are kept name-for-name in sync (core:model persists the setting; the design
// system owns the palettes), so map by name and fall back to Orbin if they ever diverge.
internal fun ColorTheme.toDesignSystem(): ColorSchemeVariant =
    runCatching { ColorSchemeVariant.valueOf(name) }.getOrDefault(ColorSchemeVariant.ORBIN)

/**
 * Settings [MaterialOrbinTheme] needs to install the Material layer without threading every
 * parameter through [OrbinNavHost].
 *
 * [fontScale] is intentionally unused by [MaterialOrbinTheme]: the root [com.orbin.uinext.NextTheme]
 * already scales [androidx.compose.ui.platform.LocalDensity], and re-applying the factor through
 * [OrbinTheme]'s typography would compound it on Material destinations.
 */
@Immutable
internal data class OrbinThemeSettings(
    val themeMode: ThemeMode,
    val colorSchemeVariant: ColorSchemeVariant,
    val dynamicColor: Boolean,
    val amoled: Boolean,
)

internal val LocalOrbinThemeSettings =
    staticCompositionLocalOf {
        OrbinThemeSettings(
            themeMode = ThemeMode.SYSTEM,
            colorSchemeVariant = ColorSchemeVariant.ORBIN,
            dynamicColor = true,
            amoled = false,
        )
    }

/**
 * Restores [OrbinTheme] for Material-only surfaces (gallery, onboarding, legacy lists, dialogs)
 * that live under the root NextTheme. Next destinations must not wrap this — they inherit the
 * Next palette and must not pay a second MaterialTheme.
 */
@Composable
internal fun MaterialOrbinTheme(content: @Composable () -> Unit) {
    val settings = LocalOrbinThemeSettings.current
    OrbinTheme(
        themeMode = settings.themeMode,
        colorSchemeVariant = settings.colorSchemeVariant,
        dynamicColor = settings.dynamicColor,
        amoled = settings.amoled,
        fontScale = 1f,
        content = content,
    )
}

@Composable
internal fun ProvideOrbinThemeSettings(
    themeMode: ThemeMode,
    colorSchemeVariant: ColorSchemeVariant,
    dynamicColor: Boolean,
    amoled: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalOrbinThemeSettings provides
            OrbinThemeSettings(
                themeMode = themeMode,
                colorSchemeVariant = colorSchemeVariant,
                dynamicColor = dynamicColor,
                amoled = amoled,
            ),
        content = content,
    )
}
