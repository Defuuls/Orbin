package com.orbin.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import com.orbin.uinext.components.NextSnackbarHostState

val LocalOrbinSnackbarHostState =
    staticCompositionLocalOf<NextSnackbarHostState> {
        error("LocalOrbinSnackbarHostState was not provided")
    }

/** [uriHandler] replaces Compose's default, so every link the app opens goes through [OrbinUriHandler]. */
@Composable
fun OrbinAppProviders(
    uriHandler: UriHandler,
    content: @Composable () -> Unit,
) {
    val snackbarHostState = remember { NextSnackbarHostState() }

    CompositionLocalProvider(
        LocalOrbinSnackbarHostState provides snackbarHostState,
        LocalUriHandler provides uriHandler,
        content = content,
    )
}
