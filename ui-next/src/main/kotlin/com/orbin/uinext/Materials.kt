package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.NextMaterials

/**
 * Frosted chrome fill: translucent raised surface with a light hairline.
 *
 * minSdk is 31, so RenderEffect is available, but applying it to the chrome layer would also blur
 * labels and icons drawn inside. True content-sampling backdrop blur needs a haze host around the
 * scrolling list; until that lands these alphas + hairline give the glass read without muddying
 * type.
 */
@Composable
fun Modifier.nextFrosted(
    shape: Shape,
    hairline: Color = next.hairline,
    lightAlpha: Float = NextMaterials.BAR_FILL_LIGHT_BLUR,
    darkAlpha: Float = NextMaterials.BAR_FILL_DARK_BLUR,
): Modifier {
    val fill = next.raised.copy(alpha = if (next.dark) darkAlpha else lightAlpha)
    return this
        .clip(shape)
        .background(fill)
        .border(0.5.dp, hairline, shape)
}
