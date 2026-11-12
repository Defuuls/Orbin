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
    val colors = reader
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val dark = next.dark
    Canvas(Modifier.size(width = 46.dp, height = 27.dp)) {
        val trackColor =
            if (checked) {
                colors.accent.copy(
                    alpha = 0.3f,
                )
            } else {
                (if (dark) Color.White else Color.Black).copy(alpha = 0.3f)
            }
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(8.dp.toPx(), (size.height - 14.dp.toPx()) / 2),
            size = Size(30.dp.toPx(), 14.dp.toPx()),
            cornerRadius = CornerRadius(7.dp.toPx()),
        )
        val thumbX = if (checked != rtl) size.width - 13.dp.toPx() else 13.dp.toPx()
        val center = Offset(thumbX, size.height / 2)
        drawCircle(Color.Black.copy(alpha = 0.12f), 10.dp.toPx(), center + Offset(0f, 1.dp.toPx()))
        drawCircle(
            color =
                if (checked) {
                    colors.accent
                } else if (dark) {
                    Color(0xFFBDBDBD)
                } else {
                    Color(0xFFECECEC)
                },
            radius = 10.dp.toPx(),
            center = center,
        )
    }
}
