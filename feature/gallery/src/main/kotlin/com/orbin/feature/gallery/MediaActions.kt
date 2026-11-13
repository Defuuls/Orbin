package com.orbin.feature.gallery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orbin.core.common.link.SafeExternalLinks
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import com.orbin.uinext.ReaderMediaSpeedOptions
import com.orbin.uinext.next
import com.orbin.uinext.reader
import com.orbin.uinext.tokens.ReaderSize

/** The viewer's ordinary overflow menu retains Orbin's file actions in Clover's popup form. */
@Composable
internal fun MediaActionsMenu(
    attachment: MediaAttachment,
    onSave: (MediaAttachment) -> Unit,
    onDismiss: () -> Unit,
    onCopyImage: ((MediaAttachment) -> Unit)? = null,
    onSaveRemaining: (() -> Unit)? = null,
    playbackSpeed: Float? = null,
    onPlaybackSpeedChange: ((Float) -> Unit)? = null,
) {
    val context = LocalContext.current
    val actions =
        listOf<Pair<String, () -> Unit>>(
            stringResource(R.string.gallery_open_in_browser) to
                { SafeExternalLinks.open(context, attachment.sourceUrl) },
            stringResource(R.string.gallery_share) to { context.shareLink(attachment.sourceUrl) },
            stringResource(R.string.gallery_copy_link) to { context.copyLink(attachment.sourceUrl) },
            stringResource(R.string.gallery_download) to {
                onSave(attachment)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, R.string.gallery_saving, Toast.LENGTH_SHORT).show()
                }
            },
        ) +
            if (attachment.type == MediaType.IMAGE && onCopyImage != null) {
                listOf(stringResource(R.string.gallery_copy_image) to { onCopyImage(attachment) })
            } else {
                emptyList()
            }
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopEnd) {
        Box(Modifier.padding(top = ReaderSize.appBar, end = 4.dp)) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = onDismiss,
                shape = RoundedCornerShape(2.dp),
                containerColor = if (next.dark) reader.surfaceVariant else reader.background,
                shadowElevation = 8.dp,
            ) {
                onSaveRemaining?.let { downloadRest ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.gallery_download_rest), color = reader.ink) },
                        onClick = {
                            onDismiss()
                            downloadRest()
                        },
                    )
                }
                if (playbackSpeed != null && onPlaybackSpeedChange != null) {
                    ReaderMediaSpeedOptions(playbackSpeed) { speed ->
                        onDismiss()
                        onPlaybackSpeedChange(speed)
                    }
                }
                actions.forEach { (label, action) ->
                    DropdownMenuItem(
                        text = { Text(label, color = reader.ink) },
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        onClick = {
                            onDismiss()
                            action()
                        },
                    )
                }
            }
        }
    }
}

internal fun Context.shareLink(url: String) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
    startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal fun Context.copyLink(url: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Orbin link", url))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, R.string.gallery_link_copied, Toast.LENGTH_SHORT).show()
    }
}
