package com.orbin.uinext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Compact preference switch; the enclosing row owns its single labeled action and checked state. */
@Composable
internal fun ReaderSettingsSwitch(checked: Boolean) {
    val colors = settingsStyle
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val dark = next.dark
    Canvas(Modifier.size(width = 51.dp, height = 31.dp)) {
        val trackColor =
            if (checked) {
                colors.accent
            } else {
                (if (dark) Color.White else Color.Black).copy(alpha = if (dark) 0.24f else 0.16f)
            }
        drawRoundRect(
            color = trackColor,
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(size.height / 2),
        )
        val thumbX = if (checked != rtl) size.width - 15.5.dp.toPx() else 15.5.dp.toPx()
        val center = Offset(thumbX, size.height / 2)
        drawCircle(Color.Black.copy(alpha = 0.12f), 13.5.dp.toPx(), center + Offset(0f, 1.dp.toPx()))
        drawCircle(
            color = Color.White,
            radius = 13.5.dp.toPx(),
            center = center,
        )
    }
}
