package com.orbin.feature.gallery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.orbin.core.model.MediaAttachment
import com.orbin.uinext.NextActionSheet
import com.orbin.uinext.NextSheetAction

/**
 * What a long press on any piece of media offers: save it, share its link, or copy its link.
 *
 * The same three words wherever media is long-pressed, so nothing about them has to be learned
 * twice.
 */
@Composable
internal fun MediaActionsSheet(
    attachment: MediaAttachment,
    onSave: (MediaAttachment) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    NextActionSheet(
        title = attachment.originalFileName,
        actions =
            listOf(
                NextSheetAction(stringResource(R.string.gallery_download)) {
                    onSave(attachment)
                    Toast.makeText(context, R.string.gallery_saving, Toast.LENGTH_SHORT).show()
                },
                NextSheetAction(stringResource(R.string.gallery_share)) { context.shareLink(attachment.sourceUrl) },
                NextSheetAction(stringResource(R.string.gallery_copy_link)) { context.copyLink(attachment.sourceUrl) },
            ),
        onDismiss = onDismiss,
    )
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
    Toast.makeText(this, R.string.gallery_link_copied, Toast.LENGTH_SHORT).show()
}
