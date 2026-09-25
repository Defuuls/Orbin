package com.orbin.ios

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.MainScope
import platform.UIKit.UIViewController

/**
 * The app's root view controller, which the Swift side hosts full screen. One HTTP client serves
 * both the providers and image loading, so every request carries the same headers.
 */
@Suppress("FunctionName", "unused") // Called from Swift as MainViewControllerKt.MainViewController().
fun MainViewController(): UIViewController {
    val client = orbinHttpClient(Darwin.create())
    val browser = Browser(orbinProviders(client), MainScope())
    return ComposeUIViewController {
        setSingletonImageLoaderFactory { context ->
            ImageLoader
                .Builder(context)
                .components { add(KtorNetworkFetcherFactory(httpClient = { client })) }
                .build()
        }
        remember { browser }.let { OrbinApp(it) }
    }
}
