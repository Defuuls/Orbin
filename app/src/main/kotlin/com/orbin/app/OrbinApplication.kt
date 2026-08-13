package com.orbin.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.orbin.data.diagnostics.DiagnosticsRepositoryImpl
import com.orbin.data.worker.WatchScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point. `@HiltAndroidApp` triggers Hilt code generation and creates the
 * application-level dependency container. Implementing [SingletonImageLoader.Factory] hands Coil
 * the DI-built [ImageLoader], and [Configuration.Provider] supplies WorkManager a Hilt-aware
 * worker factory so the background thread-watch worker can be constructor-injected.
 */
@HiltAndroidApp
class OrbinApplication :
    Application(),
    SingletonImageLoader.Factory,
    Configuration.Provider {
    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var watchScheduler: WatchScheduler

    @Inject
    lateinit var diagnosticsRepository: DiagnosticsRepositoryImpl

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        // Before anything else that could throw: an uncaught exception during startup is exactly
        // the case this records, and a handler installed later would miss it.
        diagnosticsRepository.install()
        // Schedule watched thread updates on a background dispatcher to avoid blocking startup.
        CoroutineScope(Dispatchers.Default).launch {
            watchScheduler.ensureScheduled()
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader
}
