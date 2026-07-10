package com.orbin.app

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.orbin.core.ui.post.LinkVerifier
import com.orbin.core.ui.post.LocalLinkVerifier

val LocalOrbinSnackbarHostState =
    staticCompositionLocalOf<SnackbarHostState> {
        error("LocalOrbinSnackbarHostState was not provided")
    }

@Composable
fun OrbinAppProviders(
    linkVerifier: LinkVerifier? = null,
    content: @Composable () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    CompositionLocalProvider(
        LocalOrbinSnackbarHostState provides snackbarHostState,
        LocalLinkVerifier provides linkVerifier,
        content = content,
    )
}
