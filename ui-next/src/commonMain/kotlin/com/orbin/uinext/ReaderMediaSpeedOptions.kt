package com.orbin.uinext

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_media_speed_option
import org.jetbrains.compose.resources.stringResource

/** Entries for the viewer's ordinary menu; choosing a rate never toggles playback. */
@Composable
fun ReaderMediaSpeedOptions(
    playbackSpeed: Float,
    onSpeedChange: (Float) -> Unit,
) {
    mediaPlaybackSpeeds.forEach { speed ->
        val current = speed == mediaPlaybackSpeed(playbackSpeed)
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.reader_media_speed_option, mediaPlaybackSpeedLabel(speed))) },
            onClick = { onSpeedChange(speed) },
            modifier = Modifier.semantics { selected = current },
            leadingIcon = {
                if (current) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(24.dp))
                } else {
                    Spacer(Modifier.size(24.dp))
                }
            },
        )
    }
}
