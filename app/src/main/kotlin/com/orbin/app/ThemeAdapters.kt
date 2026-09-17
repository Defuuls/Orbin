package com.orbin.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.orbin.core.designsystem.theme.ColorSchemeVariant
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.ColorTheme

/**
 * Maps persisted theme settings onto the Next theme layer the activity hosts.
 *
 * Kept out of [MainActivity] so the activity file stays the splash / lock / permission host
 * rather than also owning enum adapters.
 *
 * [isDark] resolves the System/Light/Dark preference to a plain boolean for `:ui-next`, which has
 * one dark palette and one light one rather than a mode. SYSTEM is answered here rather than left
 * to NextTheme's own default, so the shell reads the setting in one composition.
 *
 * The former [MaterialOrbinTheme] adapter was removed once nested Material sliders / snackbars /
 * pull-to-refresh were replaced with Next controls — reachable UI no longer needs a second
 * OrbinTheme colorScheme install for those widgets.
 */
@Composable
internal fun AppThemeMode.isDark(): Boolean =
    when (this) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

// The two enums are kept name-for-name in sync (core:model persists the setting; the design
// system owns the palettes), so map by name and fall back to Orbin if they ever diverge.
internal fun ColorTheme.toDesignSystem(): ColorSchemeVariant =
    runCatching { ColorSchemeVariant.valueOf(name) }.getOrDefault(ColorSchemeVariant.ORBIN)
