package com.orbin.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The handful of colors an imageboard CSS theme actually specifies. 8chan's themes (and most
 * vichan/LynxChan skins) are expressed as a small set of CSS custom properties; these seeds capture
 * that palette so [chanColorScheme] can expand it into a full Material 3 [ColorScheme] without
 * hand-authoring 30 roles per theme.
 *
 * @property dark whether the theme reads as dark (chooses the Material base + on-color contrast).
 * @property background page background (`--background-color`).
 * @property surface post/panel background (`--contrast-color`).
 * @property surfaceAlt secondary panel/menu background (`--menu-color`).
 * @property onSurface body text (`--text-color`).
 * @property primary link / accent color (`--link-color`).
 * @property primaryVariant link-hover / secondary accent (`--link-hover-color`).
 * @property outline border color (`--border-color`).
 * @property highlight highlighted/marked post background (`--background-highlight-color`).
 * @property subject subject / error accent (`--subject-color`).
 */
data class ChanThemeSeeds(
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val onSurface: Color,
    val primary: Color,
    val primaryVariant: Color,
    val outline: Color,
    val highlight: Color,
    val subject: Color,
)

/** Black or white, whichever reads better on [background]. */
private fun onColorFor(background: Color): Color = if (background.luminance() > CONTRAST_THRESHOLD) Color.Black else Color.White

/** Blends [this] toward [other] by [fraction] (0 = unchanged, 1 = [other]). */
private fun Color.blend(
    other: Color,
    fraction: Float,
): Color =
    Color(
        red = red + (other.red - red) * fraction,
        green = green + (other.green - green) * fraction,
        blue = blue + (other.blue - blue) * fraction,
        alpha = 1f,
    )

/**
 * Expands [seeds] into a full Material 3 [ColorScheme], seeding every role from the theme's few
 * real colors and deriving on-colors and container elevations by contrast and blending. This keeps
 * component surfaces legible even for themes that only specify a background and a link color.
 */
fun chanColorScheme(seeds: ChanThemeSeeds): ColorScheme {
    val base = if (seeds.dark) darkColorScheme() else lightColorScheme()
    val elevated = if (seeds.dark) Color.White else Color.Black
    return base.copy(
        primary = seeds.primary,
        onPrimary = onColorFor(seeds.primary),
        primaryContainer = seeds.primary.blend(seeds.background, PRIMARY_CONTAINER_BLEND),
        onPrimaryContainer = onColorFor(seeds.primary.blend(seeds.background, PRIMARY_CONTAINER_BLEND)),
        secondary = seeds.primaryVariant,
        onSecondary = onColorFor(seeds.primaryVariant),
        secondaryContainer = seeds.highlight,
        onSecondaryContainer = onColorFor(seeds.highlight),
        tertiary = seeds.primaryVariant,
        onTertiary = onColorFor(seeds.primaryVariant),
        tertiaryContainer = seeds.surfaceAlt,
        onTertiaryContainer = seeds.onSurface,
        error = seeds.subject,
        onError = onColorFor(seeds.subject),
        errorContainer = seeds.subject.blend(seeds.background, PRIMARY_CONTAINER_BLEND),
        onErrorContainer = onColorFor(seeds.subject.blend(seeds.background, PRIMARY_CONTAINER_BLEND)),
        background = seeds.background,
        onBackground = seeds.onSurface,
        surface = seeds.background,
        onSurface = seeds.onSurface,
        surfaceVariant = seeds.surface,
        onSurfaceVariant = seeds.onSurface.blend(seeds.background, MUTED_TEXT_BLEND),
        surfaceTint = seeds.primary,
        outline = seeds.outline,
        outlineVariant = seeds.outline.blend(seeds.background, OUTLINE_VARIANT_BLEND),
        inverseSurface = seeds.onSurface,
        inverseOnSurface = seeds.background,
        inversePrimary = seeds.primaryVariant,
        surfaceContainerLowest = seeds.background.blend(elevated, CONTAINER_LOWEST_BLEND),
        surfaceContainerLow = seeds.background.blend(elevated, CONTAINER_LOW_BLEND),
        surfaceContainer = seeds.surface,
        surfaceContainerHigh = seeds.surface.blend(elevated, CONTAINER_HIGH_BLEND),
        surfaceContainerHighest = seeds.surfaceAlt.blend(elevated, CONTAINER_HIGH_BLEND),
    )
}

private const val CONTRAST_THRESHOLD = 0.5f
private const val PRIMARY_CONTAINER_BLEND = 0.7f
private const val MUTED_TEXT_BLEND = 0.25f
private const val OUTLINE_VARIANT_BLEND = 0.5f
private const val CONTAINER_LOWEST_BLEND = 0.03f
private const val CONTAINER_LOW_BLEND = 0.06f
private const val CONTAINER_HIGH_BLEND = 0.08f
