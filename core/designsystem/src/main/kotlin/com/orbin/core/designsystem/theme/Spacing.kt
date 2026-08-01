package com.orbin.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Orbin's modern 8dp grid-based spacing scale.
 * Ensures consistent spacing throughout the app and improves visual harmony.
 * Based on Material Design 3 spacing guidelines.
 */
object ModernSpacing {
    // Extra small
    val xs: Dp = 2.dp
    val xs2: Dp = 4.dp

    // Small
    val sm: Dp = 8.dp
    val sm2: Dp = 12.dp

    // Medium
    val md: Dp = 16.dp
    val md2: Dp = 20.dp

    // Large
    val lg: Dp = 24.dp
    val lg2: Dp = 28.dp

    // Extra large
    val xl: Dp = 32.dp
    val xl2: Dp = 36.dp

    // Content padding
    val contentPadding: Dp = 16.dp
    val screenPadding: Dp = 16.dp
    val sectionPadding: Dp = 24.dp

    // Item spacing in lists
    val itemSpacing: Dp = 8.dp
    val cardSpacing: Dp = 16.dp

    // Button and interactive sizing
    val minTouchTarget: Dp = 48.dp
    val preferredTouchTarget: Dp = 56.dp
}

// Convenient aliases for common use cases
val xs = ModernSpacing.xs
val xs2 = ModernSpacing.xs2
val sm = ModernSpacing.sm
val sm2 = ModernSpacing.sm2
val md = ModernSpacing.md
val md2 = ModernSpacing.md2
val lg = ModernSpacing.lg
val lg2 = ModernSpacing.lg2
val xl = ModernSpacing.xl
val xl2 = ModernSpacing.xl2
