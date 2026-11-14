@file:Suppress("MagicNumber")

package com.orbin.uinext

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/**
 * In-app Midnight Electric Orbin mark (`>>` quote chevrons + interlocking play spark)
 * matching `docs/assets/orbin-icon-mark.svg`.
 */
@Composable
fun OrbinLogoMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sx = w / LOGO_CANVAS_SIZE
        val sy = h / LOGO_CANVAS_SIZE

        drawRoundRect(
            color = LogoMidnightNavy,
            cornerRadius = CornerRadius(w * LOGO_CORNER_RATIO, h * LOGO_CORNER_RATIO),
        )

        val chevronBrush =
            Brush.linearGradient(
                colorStops =
                    arrayOf(
                        0f to LogoElectricIndigo,
                        CHEVRON_MID_STOP to LogoElectricBlue,
                        1f to LogoElectricCyan,
                    ),
                start = Offset(CHEVRON_START_X * sx, CHEVRON_TOP_Y * sy),
                end = Offset(CHEVRON_END_X * sx, CHEVRON_BOTTOM_Y * sy),
            )

        val sparkBrush =
            Brush.linearGradient(
                colors = listOf(LogoSparkBlue, LogoSparkCyan),
                start = Offset(SPARK_START_X * sx, SPARK_TOP_Y * sy),
                end = Offset(SPARK_END_X * sx, SPARK_BOTTOM_Y * sy),
            )

        drawPath(path = leftChevronPath(sx, sy), brush = chevronBrush)
        drawPath(path = rightChevronPath(sx, sy), brush = chevronBrush)
        drawPath(path = sparkPath(sx, sy), brush = sparkBrush)
    }
}

private fun leftChevronPath(
    sx: Float,
    sy: Float,
): Path =
    Path().apply {
        moveTo(192f * sx, 262f * sy)
        lineTo(294f * sx, 262f * sy)
        lineTo(508f * sx, 512f * sy)
        lineTo(294f * sx, 762f * sy)
        lineTo(192f * sx, 762f * sy)
        lineTo(406f * sx, 512f * sy)
        close()
    }

private fun rightChevronPath(
    sx: Float,
    sy: Float,
): Path =
    Path().apply {
        moveTo(358f * sx, 262f * sy)
        lineTo(460f * sx, 262f * sy)
        lineTo(674f * sx, 512f * sy)
        lineTo(460f * sx, 762f * sy)
        lineTo(358f * sx, 762f * sy)
        lineTo(572f * sx, 512f * sy)
        close()
    }

private fun sparkPath(
    sx: Float,
    sy: Float,
): Path =
    Path().apply {
        moveTo(652f * sx, 384f * sy)
        lineTo(832f * sx, 512f * sy)
        lineTo(652f * sx, 640f * sy)
        lineTo(702f * sx, 512f * sy)
        close()
    }

private const val LOGO_CANVAS_SIZE = 1024f
private const val LOGO_CORNER_RATIO = 0.22f
private const val CHEVRON_MID_STOP = 0.55f
private const val CHEVRON_START_X = 192f
private const val CHEVRON_TOP_Y = 262f
private const val CHEVRON_END_X = 674f
private const val CHEVRON_BOTTOM_Y = 762f
private const val SPARK_START_X = 652f
private const val SPARK_TOP_Y = 384f
private const val SPARK_END_X = 832f
private const val SPARK_BOTTOM_Y = 640f

private val LogoMidnightNavy = Color(0xFF0B1021)
private val LogoElectricIndigo = Color(0xFF5B5BFF)
private val LogoElectricBlue = Color(0xFF229BFF)
private val LogoElectricCyan = Color(0xFF00D4FF)
private val LogoSparkBlue = Color(0xFF38BDF8)
private val LogoSparkCyan = Color(0xFF67E8F9)
