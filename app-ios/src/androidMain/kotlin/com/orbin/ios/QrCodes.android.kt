package com.orbin.ios

import androidx.compose.ui.graphics.ImageBitmap

internal actual fun qrCodeImage(payload: String): ImageBitmap? = null

internal actual fun scanQrCode(
    onResult: (String) -> Unit,
    onFailure: (String) -> Unit,
) = onFailure("QR scanning is only available on iOS")
