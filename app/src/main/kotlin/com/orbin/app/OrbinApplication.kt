package com.orbin.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. `@HiltAndroidApp` triggers Hilt code generation and creates the
 * application-level dependency container. Implementing [SingletonImageLoader.Factory] hands Coil
 * the DI-built [ImageLoader] (shared OkHttp client, configured caches) as the process singleton.
 */
@HiltAndroidApp
class OrbinApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject
    lateinit var imageLoader: ImageLoader

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader
}
