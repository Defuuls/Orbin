package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderSize

object DownloadTags {
    const val LIST = "orbin-download-list"

    fun row(id: Long): String = "orbin-download:$id"
}

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
    val iconShape = RoundedCornerShape(OrbinRadius.thumbnail)
    val boundedProgress = progress?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
    val stackRetry = LocalDensity.current.fontScale >= 1.6f
    OrbinCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = reader.accent,
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(iconShape)
                        .background(next.accentContainer)
                        .padding(12.dp),
            )
            WidthSpacer(12)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = fileName,
                    style = OrbinType.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = reader.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaLine(
                    if (active &&
                        boundedProgress != null
                    ) {
                        "$status · ${(boundedProgress * PERCENT).toInt()}%"
                    } else {
                        status
                    },
                )
                if (active) NextLinearProgress(progress = boundedProgress)
                if (stackRetry && onRetry != null) DownloadRetry(retryLabel, retryDescription, onRetry)
            }
            if (!stackRetry && onRetry != null) {
                DownloadRetry(retryLabel, retryDescription, onRetry)
            }
        }
    }
}

@Composable
private fun DownloadRetry(
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .heightIn(min = ReaderSize.touchTarget)
                .widthIn(min = ReaderSize.touchTarget)
                .nextClickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = OrbinType.label.withReaderFont(),
            color = reader.accent,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

private const val PERCENT = 100
