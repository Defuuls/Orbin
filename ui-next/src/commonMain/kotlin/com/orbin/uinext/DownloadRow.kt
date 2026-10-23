package com.orbin.uinext

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.NextType

/**
 * One saved file in the downloads list, the same on Android and iOS: its name, its [status] with a
 * percentage while [active] and the size is known, a progress bar while [active], and a retry
 * action when [onRetry] is given (a failed download).
 */
@Composable
fun DownloadRow(
    fileName: String,
    status: String,
    progress: Float?,
    active: Boolean,
    retryLabel: String,
    modifier: Modifier = Modifier,
    // What a screen reader announces for the retry action, when it should say more than the label.
    retryDescription: String = retryLabel,
    onRetry: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = NextSpace.rowX, vertical = NextSpace.rowY),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = fileName,
                style = NextType.body,
                fontWeight = FontWeight.Medium,
                color = next.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MetaLine(
                if (active && progress != null) "$status · ${(progress * PERCENT).toInt()}%" else status,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (active) {
                NextLinearProgress(progress = progress, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (onRetry != null) {
            InlineAction(
                label = retryLabel,
                accent = true,
                onClick = onRetry,
                modifier = Modifier.semantics { contentDescription = retryDescription },
            )
        }
    }
}

private const val PERCENT = 100
