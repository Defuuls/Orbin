package com.orbin.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * Checks the body-text contrast ([ChanThemeSeeds.onSurface] against [ChanThemeSeeds.background])
 * of every ported imageboard skin against the WCAG AA threshold for normal text (4.5:1).
 *
 * These skins were ported verbatim from their source CSS, and several fall short of AA — that is a
 * deliberate authenticity tradeoff, not an oversight, so known offenders are recorded in
 * [KNOWN_LOW_CONTRAST_SKINS] rather than silently allowed. Any *new* skin failing the threshold
 * fails this test, forcing a conscious decision (fix the seed, or add it to the exemption list with
 * a reason) instead of shipping a hard-to-read theme by accident.
 */
class ChanThemeContrastTest {
    @Test
    fun everyPortedSkinMeetsAaContrastOrIsAnExplicitlyKnownExemption() {
        val failures =
            ColorSchemeVariant.entries
                .mapNotNull { variant -> variant.seeds?.let { variant to it } }
                .associate { (variant, seeds) -> variant to contrastRatio(seeds.onSurface, seeds.background) }
                .filterValues { ratio -> ratio < WCAG_AA_NORMAL_TEXT_RATIO }

        val unexpectedFailures = failures.keys - KNOWN_LOW_CONTRAST_SKINS
        assertWithMessage(
            "New skin(s) fall below WCAG AA (4.5:1) body-text contrast: $unexpectedFailures. " +
                "Either adjust the seed's onSurface/background, or add it to " +
                "KNOWN_LOW_CONTRAST_SKINS with a reason if the low contrast is an intentional " +
                "authenticity tradeoff.",
        ).that(unexpectedFailures).isEmpty()

        val noLongerFailing = KNOWN_LOW_CONTRAST_SKINS - failures.keys
        assertWithMessage(
            "Skin(s) listed in KNOWN_LOW_CONTRAST_SKINS now meet WCAG AA contrast: " +
                "$noLongerFailing. Remove them from the exemption list.",
        ).that(noLongerFailing).isEmpty()
    }

    /** WCAG relative-luminance contrast ratio: (L1 + 0.05) / (L2 + 0.05), L1 the lighter color. */
    private fun contrastRatio(
        a: Color,
        b: Color,
    ): Float {
        val lighter = max(a.luminance(), b.luminance())
        val darker = min(a.luminance(), b.luminance())
        return (lighter + LUMINANCE_OFFSET) / (darker + LUMINANCE_OFFSET)
    }

    private companion object {
        const val WCAG_AA_NORMAL_TEXT_RATIO = 4.5f
        const val LUMINANCE_OFFSET = 0.05f

        // Ported CSS skins whose body text falls under 4.5:1 in their source theme. Kept faithful
        // to the original rather than "corrected", since users pick these skins for the look, not
        // for AA compliance.
        val KNOWN_LOW_CONTRAST_SKINS =
            setOf(
                ColorSchemeVariant.HISPAPERRO,
            )
    }
}
