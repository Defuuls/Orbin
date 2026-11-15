package com.orbin.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.OrbinType

object ErrorNoteTags {
    const val ICON = "reader-error-icon"
}

/** The icon every error shows beside its text, so an error never depends on its color alone. */
val ErrorIcon = Icons.Filled.Warning

/** An inline error message: the error color with [ErrorIcon], read out as the text alone. */
@Composable
fun ErrorNote(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = OrbinType.body,
    textAlign: TextAlign? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            ErrorIcon,
            contentDescription = null,
            tint = reader.error,
            modifier = Modifier.size(18.dp).testTag(ErrorNoteTags.ICON),
        )
        Text(text, style = style, color = reader.error, textAlign = textAlign)
    }
}
