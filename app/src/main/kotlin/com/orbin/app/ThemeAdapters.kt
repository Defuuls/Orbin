package com.orbin.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.orbin.core.designsystem.theme.ColorSchemeVariant
import com.orbin.core.designsystem.theme.ThemeMode
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.ColorTheme

/**
 * Maps persisted theme settings onto the two theme layers the activity hosts.
 *
 * Kept out of [MainActivity] so the activity file stays the splash / lock / permission host
 * rather than also owning enum adapters.
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

internal fun ColorTheme.toDesignSystem(): ColorSchemeVariant =
    runCatching { ColorSchemeVariant.valueOf(name) }.getOrDefault(ColorSchemeVariant.ORBIN)
