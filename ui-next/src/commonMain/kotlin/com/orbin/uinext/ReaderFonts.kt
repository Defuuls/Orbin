package com.orbin.uinext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_roboto_bold
import com.orbin.uinext.resources.reader_roboto_bold_italic
import com.orbin.uinext.resources.reader_roboto_condensed_regular
import com.orbin.uinext.resources.reader_roboto_italic
import com.orbin.uinext.resources.reader_roboto_medium
import com.orbin.uinext.resources.reader_roboto_medium_italic
import com.orbin.uinext.resources.reader_roboto_regular
import com.orbin.uinext.tokens.LocalNextFontFamily
import org.jetbrains.compose.resources.Font

/** Local, Apache-licensed Roboto faces; their exact sources and hashes ship with the font notices. */
@Composable
internal fun rememberReaderRobotoFontFamily(): FontFamily {
    val regular = Font(Res.font.reader_roboto_regular, FontWeight.Normal)
    val italic = Font(Res.font.reader_roboto_italic, FontWeight.Normal, FontStyle.Italic)
    val medium = Font(Res.font.reader_roboto_medium, FontWeight.Medium)
    val mediumItalic = Font(Res.font.reader_roboto_medium_italic, FontWeight.Medium, FontStyle.Italic)
    val bold = Font(Res.font.reader_roboto_bold, FontWeight.Bold)
    val boldItalic = Font(Res.font.reader_roboto_bold_italic, FontWeight.Bold, FontStyle.Italic)
    return remember(regular, italic, medium, mediumItalic, bold, boldItalic) {
        FontFamily(regular, italic, medium, mediumItalic, bold, boldItalic)
    }
}

/** The reference's separately bundled condensed face remains available for an explicit font choice. */
@Composable
internal fun rememberReaderCondensedFontFamily(): FontFamily {
    val condensed = Font(Res.font.reader_roboto_condensed_regular, FontWeight.Normal)
    return remember(condensed) { FontFamily(condensed) }
}

/**
 * Applies the composition's reader font at the rendering boundary. Reader token metrics remain
 * pure, reusable TextStyle constants; inside NextTheme they use bundled Roboto on both platforms.
 * Explicit other families and every requested size, weight, style and synthesis setting remain.
 */
@Composable
fun TextStyle.withReaderFont(): TextStyle =
    if (fontFamily == null || fontFamily == FontFamily.Default || fontFamily == FontFamily.SansSerif) {
        copy(fontFamily = LocalNextFontFamily.current)
    } else {
        this
    }
