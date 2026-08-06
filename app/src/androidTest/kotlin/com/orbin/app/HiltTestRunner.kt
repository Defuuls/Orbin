package com.orbin.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Swaps in the generated test application so instrumentation tests get a Hilt component without
 * booting `OrbinApplication`. Referenced by `testInstrumentationRunner` in the app's build file.
 *
 * `TestApplication_Application` is generated from [WorkManagerAwareTestApplication] and keeps the
 * WorkManager configuration the real application provides — the manifest removes the default
 * initializer, so without it WorkManager is never set up under test. Referencing the class rather
 * than naming it as a string means a rename fails the build instead of the device.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(classLoader, TestApplication_Application::class.java.name, context)
}
