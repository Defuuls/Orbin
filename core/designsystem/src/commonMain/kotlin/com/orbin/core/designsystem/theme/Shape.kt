package com.orbin.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Orbin's M3 Expressive shape scale.
 *
 * Material 3 Expressive pushes corners large — surfaces feel open and approachable.
 * These values align with the M3 Expressive guidance from Google I/O 2025.
 */
val OrbinShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )
