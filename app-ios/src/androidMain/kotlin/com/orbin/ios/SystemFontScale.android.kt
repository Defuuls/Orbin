package com.orbin.ios

import androidx.compose.runtime.Composable

/** Host-test / JVM target: no Dynamic Type bridge; leave NextTheme at the density already in force. */
@Composable
internal actual fun rememberSystemFontScale(): Float = 1f
