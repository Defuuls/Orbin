package com.orbin.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.orbin.uinext.NextSnackbarHostState

val LocalOrbinSnackbarHostState =
    staticCompositionLocalOf<NextSnackbarHostState> {
        error("LocalOrbinSnackbarHostState was not provided")
    }

@Composable
fun OrbinAppProviders(content: @Composable () -> Unit) {
    val snackbarHostState = remember { NextSnackbarHostState() }

    CompositionLocalProvider(
        LocalOrbinSnackbarHostState provides snackbarHostState,
        content = content,
    )
}
