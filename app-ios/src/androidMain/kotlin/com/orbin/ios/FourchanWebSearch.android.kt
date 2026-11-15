package com.orbin.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.orbin.provider.api.FourchanSearchSession
import kotlinx.coroutines.CoroutineScope

/** Shared-logic test target only; production Android uses feature:search's retained WebView. */
@Suppress("UNUSED_PARAMETER")
internal actual class NativeSearchTransport actual constructor(
    actual val session: FourchanSearchSession,
    scope: CoroutineScope,
) {
    @Composable actual fun Content(modifier: Modifier) {
        Box(modifier)
    }

    actual fun close() = Unit
}
