package com.orbin.minimal

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point for the pared-back reader.
 *
 * Deliberately thinner than the full client's: no crash diagnostics recorder, and no watched-thread
 * scheduler, because this build has neither a diagnostics screen to read them in nor thread
 * watching to schedule. What remains is what the shared layers require — Hilt, Coil's image loader,
 * and a Hilt-aware WorkManager configuration, which the data layer's workers are built against.
 */
@HiltAndroidApp
class MinimalApplication :
    Application(),
    SingletonImageLoader.Factory,
    Configuration.Provider {
    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader
}
