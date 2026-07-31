package com.orbin.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Orbin's shared shape scale for Material components and custom surfaces.
 * Updated to modern Material Design 3 standards with larger radii for contemporary aesthetics.
 */
val OrbinShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),  // Increased from 4dp
        small = RoundedCornerShape(12.dp),      // Increased from 8dp
        medium = RoundedCornerShape(16.dp),     // Increased from 12dp
        large = RoundedCornerShape(20.dp),      // Increased from 16dp
        extraLarge = RoundedCornerShape(28.dp), // Increased from 24dp
    )

/** Additional shape tokens for specialized use cases. */
object ModernShapes {
    val none = RoundedCornerShape(0.dp)
    val tiny = RoundedCornerShape(4.dp)
    val verySmall = RoundedCornerShape(6.dp)
    val small = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(20.dp)
    val huge = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(50.dp) // For chips and pills
}
