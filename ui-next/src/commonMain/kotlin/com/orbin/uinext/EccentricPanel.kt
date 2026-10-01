package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Formerly the neo-brutalist "Core Override" panel with spinning CSS animations.
 * Deprecated and removed from active UI in accordance with Material Design 3 migration.
 */
@Deprecated(
    message = "EccentricPanel has been deprecated and replaced by standard Material 3 components.",
    level = DeprecationLevel.WARNING,
)
@Composable
fun EccentricPanel(modifier: Modifier = Modifier) {
    // Deprecated: No-op placeholder with zero spinning animations or neo-brutalist elements.
    Box(modifier = modifier)
}
