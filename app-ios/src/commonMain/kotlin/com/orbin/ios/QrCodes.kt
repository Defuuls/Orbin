package com.orbin.ios

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp

/** [payload] as a QR code, one pixel per module (scale it without filtering), or null if it can't be drawn. */
internal expect fun qrCodeImage(payload: String): ImageBitmap?

/**
 * Opens the camera to scan one QR code. [onResult] gets its text; [onFailure] a sentence when
 * there is no camera or access was refused. Closing the scanner calls neither.
 */
internal expect fun scanQrCode(
    onResult: (String) -> Unit,
    onFailure: (String) -> Unit,
)

/** A pairing or setup code as a QR code, as Android shows it. */
@Composable
internal fun QrCodeDialog(
    title: String,
    payload: String,
    note: String,
    onDismiss: () -> Unit,
) {
    val bitmap = remember(payload) { qrCodeImage(payload) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = title,
                        filterQuality = FilterQuality.None,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                // Scanners need dark modules on a light field, whatever the app theme.
                                .background(Color.White)
                                .padding(8.dp),
                    )
                }
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
