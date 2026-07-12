package com.orbin.app

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

val LocalOrbinSnackbarHostState =
    staticCompositionLocalOf<SnackbarHostState> {
        error("LocalOrbinSnackbarHostState was not provided")
    }

@Composable
fun OrbinAppProviders(content: @Composable () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }

    CompositionLocalProvider(
        LocalOrbinSnackbarHostState provides snackbarHostState,
    ) {
        SelectionContainer(content = content)
    }
}
