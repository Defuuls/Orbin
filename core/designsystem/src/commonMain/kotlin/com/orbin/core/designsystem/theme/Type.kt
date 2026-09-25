package com.orbin.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle

/**
 * Builds Orbin's complete Material typography scale, adjusted by the in-app [fontScale] setting.
 * System font scaling still applies normally; this multiplier is the user's Orbin-specific
 * appearance preference.
 */
@Composable
fun orbinTypography(fontScale: Float = 1f): Typography =
    remember(fontScale) {
        val base = Typography()
        base.copy(
            displayLarge = base.displayLarge.scaledBy(fontScale),
            displayMedium = base.displayMedium.scaledBy(fontScale),
            displaySmall = base.displaySmall.scaledBy(fontScale),
            headlineLarge = base.headlineLarge.scaledBy(fontScale),
            headlineMedium = base.headlineMedium.scaledBy(fontScale),
            headlineSmall = base.headlineSmall.scaledBy(fontScale),
            titleLarge = base.titleLarge.scaledBy(fontScale),
            titleMedium = base.titleMedium.scaledBy(fontScale),
            titleSmall = base.titleSmall.scaledBy(fontScale),
            bodyLarge = base.bodyLarge.scaledBy(fontScale),
            bodyMedium = base.bodyMedium.scaledBy(fontScale),
            bodySmall = base.bodySmall.scaledBy(fontScale),
            labelLarge = base.labelLarge.scaledBy(fontScale),
            labelMedium = base.labelMedium.scaledBy(fontScale),
            labelSmall = base.labelSmall.scaledBy(fontScale),
        )
    }

private fun TextStyle.scaledBy(fontScale: Float): TextStyle =
    copy(
        fontSize = fontSize * fontScale,
        lineHeight = lineHeight * fontScale,
    )
