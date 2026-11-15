package com.orbin.uinext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Settings roles use the same quiet surfaces and Orbin accents as the rest of the app. */
@Immutable
internal data class SettingsColors(
    val background: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val accent: Color,
    val accentInk: Color,
    val onAccent: Color,
    val secondary: Color,
    val danger: Color,
) {
    val ink: Color get() = text
    val accent2: Color get() = secondary
}

/** Keep preference presentation aligned with the shared reader palette. */
internal val settingsStyle: SettingsColors
    @Composable get() {
        val colors = reader
        return SettingsColors(
            background = colors.background,
            card = colors.surfaceVariant,
            text = colors.ink,
            muted = colors.inkMuted,
            border = colors.hairline,
            accent = colors.accent,
            accentInk = colors.accent,
            onAccent = colors.onAccent,
            secondary = Color(0xFFA78BFA),
            danger = colors.error,
        )
    }

@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = settingsStyle
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(content = content)
    }
}

@Composable
internal fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = SettingsCard(modifier, content)

/** The label and action live on the row; its colored icon is decorative. */
@Composable
internal fun SettingsIconTile(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(32.dp)
                .background(tint.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
    }
}
