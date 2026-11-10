package com.orbin.uinext

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbin.uinext.tokens.LocalNextFontFamily

/** Shared geometry and colors for the short-form reader on both platforms. */
object TikTokDesign {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Pink = Color(0xFFFE2C55)
    val Cyan = Color(0xFF25F4EE)
    val SecondaryWhite = Color(0xFFB8B8B8)
    val Divider = Color(0xFF272727)

    val NavigationHeight = 52.dp
    val NavigationIconSize = 24.dp
    val ActionIconSize = 32.dp
    val FeedHeaderHeight = 48.dp

    val NavigationLabel: TextStyle
        @Composable get() =
            TextStyle(
                fontFamily = LocalNextFontFamily.current,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            )
}
