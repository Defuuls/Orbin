package com.orbin.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orbin.ios.resources.Res
import com.orbin.ios.resources.ios_media_spoiler
import com.orbin.uinext.ReaderIcons
import com.orbin.uinext.tokens.ReaderType
import com.orbin.uinext.withReaderFont
import org.jetbrains.compose.resources.stringResource

/**
 * What a spoilered file shows until it is opened: a solid blackout over its thumbnail, as
 * Android's `SpoilerOverlay` draws. Posts the violent-media cover marks
 * (`ViolentMediaCoverProvider`) arrive spoilered too, so this is also that cover.
 *
 * The fill is fully opaque so shape and colour cannot be guessed through it.
 */
@Composable
internal fun SpoilerCover(
    modifier: Modifier = Modifier,
    text: String = stringResource(Res.string.ios_media_spoiler),
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = ReaderIcons.Hide,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = text,
                color = Color.White,
                style = ReaderType.label.withReaderFont(),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp),
            )
        }
    }
}
