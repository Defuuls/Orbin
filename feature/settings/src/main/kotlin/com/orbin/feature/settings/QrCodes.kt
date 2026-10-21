package com.orbin.feature.settings

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private const val QR_QUIET_ZONE = 2

/**
 * A pairing or setup code drawn as a QR code. The matrix is rendered one pixel per module and
 * scaled without filtering, so it stays sharp at any size and costs a few kilobytes.
 */
@Composable
internal fun QrCodeDialog(
    title: String,
    payload: String,
    note: String,
    onDismiss: () -> Unit,
) {
    val bitmap = remember(payload) { qrBitmap(payload).asImageBitmap() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Image(
                    bitmap = bitmap,
                    contentDescription = title,
                    filterQuality = FilterQuality.None,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            // Scanners need dark modules on a light field, whatever the app theme.
                            .background(androidx.compose.ui.graphics.Color.White)
                            .padding(8.dp),
                )
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

internal fun qrMatrix(payload: String): BitMatrix =
    QRCodeWriter().encode(
        payload,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(
            // M survives a glare or a scratched screen protector without making the code too dense.
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to QR_QUIET_ZONE,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        ),
    )

private fun qrBitmap(payload: String): Bitmap {
    val matrix = qrMatrix(payload)
    val pixels =
        IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE
        }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}

/**
 * Opens the camera to read one QR code; the scanner asks for the camera permission itself and
 * reports nothing if it is refused or cancelled. Returns the function that starts a scan.
 */
@Composable
internal fun rememberQrScanner(
    prompt: String,
    onScanned: (String) -> Unit,
): () -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            result.contents?.let(onScanned)
        }
    return {
        launcher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(prompt)
                .setBeepEnabled(false),
        )
    }
}
