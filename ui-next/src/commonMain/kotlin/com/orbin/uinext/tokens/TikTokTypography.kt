package com.orbin.uinext.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.tiktok_sans_bold
import com.orbin.uinext.resources.tiktok_sans_bold_italic
import com.orbin.uinext.resources.tiktok_sans_medium
import com.orbin.uinext.resources.tiktok_sans_medium_italic
import com.orbin.uinext.resources.tiktok_sans_regular
import com.orbin.uinext.resources.tiktok_sans_regular_italic
import com.orbin.uinext.resources.tiktok_sans_semibold
import com.orbin.uinext.resources.tiktok_sans_semibold_italic
import org.jetbrains.compose.resources.Font

/** Installed once by NextTheme so explicit shared text styles use the same bundled family. */
val LocalNextFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/** Unmodified OFL-licensed TikTok Sans static faces; no network or variable-font dependency. */
@Composable
fun rememberTikTokFontFamily(): FontFamily {
    val regular = Font(Res.font.tiktok_sans_regular, FontWeight.Normal)
    val medium = Font(Res.font.tiktok_sans_medium, FontWeight.Medium)
    val semibold = Font(Res.font.tiktok_sans_semibold, FontWeight.SemiBold)
    val bold = Font(Res.font.tiktok_sans_bold, FontWeight.Bold)
    val regularItalic = Font(Res.font.tiktok_sans_regular_italic, FontWeight.Normal, FontStyle.Italic)
    val mediumItalic = Font(Res.font.tiktok_sans_medium_italic, FontWeight.Medium, FontStyle.Italic)
    val semiboldItalic = Font(Res.font.tiktok_sans_semibold_italic, FontWeight.SemiBold, FontStyle.Italic)
    val boldItalic = Font(Res.font.tiktok_sans_bold_italic, FontWeight.Bold, FontStyle.Italic)
    return remember(regular, medium, semibold, bold, regularItalic, mediumItalic, semiboldItalic, boldItalic) {
        FontFamily(regular, medium, semibold, bold, regularItalic, mediumItalic, semiboldItalic, boldItalic)
    }
}
