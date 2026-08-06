package com.orbin.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.components.SingletonComponent

/**
 * Base for the generated test application.
 *
 * `HiltTestApplication` replaces [OrbinApplication] under instrumentation, and [OrbinApplication]
 * is the only thing that configures WorkManager — the manifest deliberately removes the default
 * `androidx.startup` initializer precisely because the app supplies a Hilt-aware configuration
 * itself. Swapping the application class therefore leaves WorkManager unconfigured, and anything
 * reaching it during startup fails. Re-declaring `Configuration.Provider` here restores that one
 * piece without dragging in the rest of the real application.
 *
 * The factory comes from an entry point rather than an `@Inject` field because
 * `@CustomTestApplication` rejects injected fields on the application class.
 */
open class WorkManagerAwareTestApplication :
    Application(),
    Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(
                    EntryPointAccessors
                        .fromApplication(this, WorkerFactoryEntryPoint::class.java)
                        .workerFactory(),
                ).build()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerFactoryEntryPoint {
        fun workerFactory(): HiltWorkerFactory
    }
}

/** Generates `TestApplication_Application`, which [HiltTestRunner] installs. */
@CustomTestApplication(WorkManagerAwareTestApplication::class)
interface TestApplication
