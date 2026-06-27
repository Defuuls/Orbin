package com.orbin.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Builds a [Typography] scaled by [fontScale], letting the user enlarge/shrink in-app text
 * independently of the system font size (an appearance setting). 1.0 is the Material default.
 */
@Composable
fun orbinTypography(fontScale: Float = 1f): Typography =
    remember(fontScale) {
        val base = Typography()
        if (fontScale == 1f) {
            base
        } else {
            base.copy(
                bodyLarge = base.bodyLarge.copy(fontSize = base.bodyLarge.fontSize * fontScale),
                bodyMedium = base.bodyMedium.copy(fontSize = base.bodyMedium.fontSize * fontScale),
                bodySmall = base.bodySmall.copy(fontSize = base.bodySmall.fontSize * fontScale),
                titleLarge = base.titleLarge.copy(fontSize = base.titleLarge.fontSize * fontScale),
                titleMedium = base.titleMedium.copy(fontSize = base.titleMedium.fontSize * fontScale),
                labelLarge = base.labelLarge.copy(fontSize = base.labelLarge.fontSize * fontScale),
            )
        }
    }
