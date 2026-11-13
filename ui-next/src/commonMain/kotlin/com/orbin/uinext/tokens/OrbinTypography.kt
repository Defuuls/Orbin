package com.orbin.uinext.tokens

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The single bundled-font provider installed by NextTheme on Android and iOS. */
val LocalNextFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

private fun readerType(
    size: Int,
    lineHeight: Int,
    weight: Int,
): TextStyle =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = FontWeight(weight),
    )

/** Pure compact reader metrics, exposed through OrbinType and resolved by withReaderFont. */
private object OrbinReaderStyles {
    /** App bar title. */
    val appBarTitle: TextStyle = readerType(20, 28, 500)

    /** Board line under a thread title. */
    val appBarSubtitle: TextStyle = readerType(14, 20, 400)

    /** Catalog row title. */
    val rowTitle: TextStyle = readerType(16, 22, 700)

    /** Post number, date, file details, backlinks. */
    val meta: TextStyle = readerType(12, 16, 400)

    /** Catalog row preview line. */
    val preview: TextStyle = readerType(14, 20, 400)

    /** Post body. */
    val body: TextStyle = readerType(15, 22, 400)

    /** Grid card title. */
    val cardTitle: TextStyle = readerType(14, 20, 700)

    /** Post author name. */
    val postAuthor: TextStyle = readerType(12, 16, 700)

    /** Clover's default post size; natural Roboto metrics supply its line spacing. */
    val postBody: TextStyle =
        TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, fontWeight = FontWeight.Normal)

    /** The sw600dp post size from Clover's tablet resource configuration. */
    val postBodyWide: TextStyle =
        TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, fontWeight = FontWeight.Normal)

    /** Post number, time and attachment metadata are four sp smaller than the body. */
    val postDetails: TextStyle =
        TextStyle(fontFamily = FontFamily.Default, fontSize = 10.sp, fontWeight = FontWeight.Normal)

    val postDetailsWide: TextStyle =
        TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, fontWeight = FontWeight.Normal)

    /** Reply count in the pill. */
    val pill: TextStyle = readerType(14, 20, 500)

    /** Empty and error state title. */
    val stateTitle: TextStyle = readerType(24, 32, 400)

    /** State helper text and the empty detail pane message. */
    val stateBody: TextStyle = readerType(16, 24, 400)

    /** Text buttons. */
    val label: TextStyle = readerType(14, 20, 500)
}

/** Chrome metrics; the public getters resolve the composition font without applying another scale. */
@Immutable
private object OrbinTypeBase {
    private val sans = FontFamily.Default
    private val mono = FontFamily.Default
    private const val TNUM = "tnum"

    // Display — instrument bay & primary screen readouts
    val displayLarge =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-1.7).sp,
            fontFeatureSettings = TNUM,
        )
    val displayMedium =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.8).sp,
            fontFeatureSettings = TNUM,
        )
    val displaySmall =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.8).sp,
            fontFeatureSettings = TNUM,
        )

    // Headline — screen titles, section headers
    val headlineLarge =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.6).sp,
            fontFeatureSettings = TNUM,
        )
    val headlineMedium =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.4).sp,
            fontFeatureSettings = TNUM,
        )
    val headlineSmall =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.3).sp,
            fontFeatureSettings = TNUM,
        )

    // Title — card titles, row primary text
    val titleLarge =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.2).sp,
            fontFeatureSettings = TNUM,
        )
    val titleMedium =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = (-0.1).sp,
            fontFeatureSettings = TNUM,
        )
    val titleSmall =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
            fontFeatureSettings = TNUM,
        )

    // Body — post content, descriptions
    val bodyLarge =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            letterSpacing = 0.15.sp,
            fontFeatureSettings = TNUM,
        )
    val bodyMedium =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
            fontFeatureSettings = TNUM,
        )
    val bodySmall =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.2.sp,
            fontFeatureSettings = TNUM,
        )

    // Label — chips, badges, captions, nav labels, telemetry readouts
    val labelLarge =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.3.sp,
            fontFeatureSettings = TNUM,
        )
    val labelMedium =
        TextStyle(
            fontFamily = sans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
            fontFeatureSettings = TNUM,
        )
    val labelSmall =
        TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            letterSpacing = 0.6.sp,
            fontFeatureSettings = TNUM,
        )

    /** Dedicated monospace telemetry styles for board codes, post numbers, byte counts, and HUD tags. */
    val telemetry =
        TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            letterSpacing = 0.7.sp,
            fontFeatureSettings = TNUM,
        )

    val telemetryLarge =
        TextStyle(
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.5.sp,
            fontFeatureSettings = TNUM,
        )

    // Convenience aliases used in ui-next components

    /** Screen/section primary title. */
    val largeTitle = headlineSmall

    val title1 = headlineLarge
    val title2 = headlineMedium
    val title3 = headlineSmall

    /** Row primary text. */
    val body = bodyLarge

    /** Secondary row text, timestamps. */
    val footnote = bodySmall

    /** Navigation bar label, chip text. */
    val tab = labelMedium

    /** Section header — technical index header. */
    val sectionHeader = bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)

    /** Inline action label. */
    val callout = labelLarge

    /** Caption / badge text. */
    val caption1 = labelSmall

    /** Tight badge. */
    val caption2 = labelSmall

    /** Subheadline. */
    val subheadline = titleSmall

    /** Headline row text. */
    val headline = titleMedium

    /** Dense list primary text (14sp). */
    val bodyDense = bodyMedium

    /** Dense secondary text (12sp). */
    val footnoteDense = bodySmall

    /** Extra-small telemetry caption (11sp). */
    val captionDense = labelSmall
}

/** One shared type namespace: font-resolved chrome and pure compact reader roles. */
@Immutable
object OrbinType {
    val displayLarge: TextStyle
        @Composable get() = OrbinTypeBase.displayLarge.copy(fontFamily = LocalNextFontFamily.current)

    val displayMedium: TextStyle
        @Composable get() = OrbinTypeBase.displayMedium.copy(fontFamily = LocalNextFontFamily.current)

    val displaySmall: TextStyle
        @Composable get() = OrbinTypeBase.displaySmall.copy(fontFamily = LocalNextFontFamily.current)

    val headlineLarge: TextStyle
        @Composable get() = OrbinTypeBase.headlineLarge.copy(fontFamily = LocalNextFontFamily.current)

    val headlineMedium: TextStyle
        @Composable get() = OrbinTypeBase.headlineMedium.copy(fontFamily = LocalNextFontFamily.current)

    val headlineSmall: TextStyle
        @Composable get() = OrbinTypeBase.headlineSmall.copy(fontFamily = LocalNextFontFamily.current)

    val titleLarge: TextStyle
        @Composable get() = OrbinTypeBase.titleLarge.copy(fontFamily = LocalNextFontFamily.current)

    val titleMedium: TextStyle
        @Composable get() = OrbinTypeBase.titleMedium.copy(fontFamily = LocalNextFontFamily.current)

    val titleSmall: TextStyle
        @Composable get() = OrbinTypeBase.titleSmall.copy(fontFamily = LocalNextFontFamily.current)

    val bodyLarge: TextStyle
        @Composable get() = OrbinTypeBase.bodyLarge.copy(fontFamily = LocalNextFontFamily.current)

    val bodyMedium: TextStyle
        @Composable get() = OrbinTypeBase.bodyMedium.copy(fontFamily = LocalNextFontFamily.current)

    val bodySmall: TextStyle
        @Composable get() = OrbinTypeBase.bodySmall.copy(fontFamily = LocalNextFontFamily.current)

    val labelLarge: TextStyle
        @Composable get() = OrbinTypeBase.labelLarge.copy(fontFamily = LocalNextFontFamily.current)

    val labelMedium: TextStyle
        @Composable get() = OrbinTypeBase.labelMedium.copy(fontFamily = LocalNextFontFamily.current)

    val labelSmall: TextStyle
        @Composable get() = OrbinTypeBase.labelSmall.copy(fontFamily = LocalNextFontFamily.current)

    val telemetry: TextStyle
        @Composable get() = OrbinTypeBase.telemetry.copy(fontFamily = LocalNextFontFamily.current)

    val telemetryLarge: TextStyle
        @Composable get() = OrbinTypeBase.telemetryLarge.copy(fontFamily = LocalNextFontFamily.current)

    val largeTitle: TextStyle
        @Composable get() = OrbinTypeBase.largeTitle.copy(fontFamily = LocalNextFontFamily.current)

    val title1: TextStyle
        @Composable get() = OrbinTypeBase.title1.copy(fontFamily = LocalNextFontFamily.current)

    val title2: TextStyle
        @Composable get() = OrbinTypeBase.title2.copy(fontFamily = LocalNextFontFamily.current)

    val title3: TextStyle
        @Composable get() = OrbinTypeBase.title3.copy(fontFamily = LocalNextFontFamily.current)

    val body: TextStyle
        @Composable get() = OrbinTypeBase.body.copy(fontFamily = LocalNextFontFamily.current)

    val footnote: TextStyle
        @Composable get() = OrbinTypeBase.footnote.copy(fontFamily = LocalNextFontFamily.current)

    val tab: TextStyle
        @Composable get() = OrbinTypeBase.tab.copy(fontFamily = LocalNextFontFamily.current)

    val sectionHeader: TextStyle
        @Composable get() = OrbinTypeBase.sectionHeader.copy(fontFamily = LocalNextFontFamily.current)

    val callout: TextStyle
        @Composable get() = OrbinTypeBase.callout.copy(fontFamily = LocalNextFontFamily.current)

    val caption1: TextStyle
        @Composable get() = OrbinTypeBase.caption1.copy(fontFamily = LocalNextFontFamily.current)

    val caption2: TextStyle
        @Composable get() = OrbinTypeBase.caption2.copy(fontFamily = LocalNextFontFamily.current)

    val subheadline: TextStyle
        @Composable get() = OrbinTypeBase.subheadline.copy(fontFamily = LocalNextFontFamily.current)

    val headline: TextStyle
        @Composable get() = OrbinTypeBase.headline.copy(fontFamily = LocalNextFontFamily.current)

    val bodyDense: TextStyle
        @Composable get() = OrbinTypeBase.bodyDense.copy(fontFamily = LocalNextFontFamily.current)

    val footnoteDense: TextStyle
        @Composable get() = OrbinTypeBase.footnoteDense.copy(fontFamily = LocalNextFontFamily.current)

    val captionDense: TextStyle
        @Composable get() = OrbinTypeBase.captionDense.copy(fontFamily = LocalNextFontFamily.current)

    val material: Typography
        @Composable get() =
            Typography(
                displayLarge = displayLarge,
                displayMedium = displayMedium,
                displaySmall = displaySmall,
                headlineLarge = headlineLarge,
                headlineMedium = headlineMedium,
                headlineSmall = headlineSmall,
                titleLarge = titleLarge,
                titleMedium = titleMedium,
                titleSmall = titleSmall,
                bodyLarge = bodyLarge,
                bodyMedium = bodyMedium,
                bodySmall = bodySmall,
                labelLarge = labelLarge,
                labelMedium = labelMedium,
                labelSmall = labelSmall,
            )

    val appBarTitle: TextStyle
        get() = OrbinReaderStyles.appBarTitle

    val appBarSubtitle: TextStyle
        get() = OrbinReaderStyles.appBarSubtitle

    val rowTitle: TextStyle
        get() = OrbinReaderStyles.rowTitle

    val meta: TextStyle
        get() = OrbinReaderStyles.meta

    val preview: TextStyle
        get() = OrbinReaderStyles.preview

    val readerBody: TextStyle
        get() = OrbinReaderStyles.body

    val cardTitle: TextStyle
        get() = OrbinReaderStyles.cardTitle

    val postAuthor: TextStyle
        get() = OrbinReaderStyles.postAuthor

    val postBody: TextStyle
        get() = OrbinReaderStyles.postBody

    val postBodyWide: TextStyle
        get() = OrbinReaderStyles.postBodyWide

    val postDetails: TextStyle
        get() = OrbinReaderStyles.postDetails

    val postDetailsWide: TextStyle
        get() = OrbinReaderStyles.postDetailsWide

    val pill: TextStyle
        get() = OrbinReaderStyles.pill

    val stateTitle: TextStyle
        get() = OrbinReaderStyles.stateTitle

    val stateBody: TextStyle
        get() = OrbinReaderStyles.stateBody

    val label: TextStyle
        get() = OrbinReaderStyles.label
}
