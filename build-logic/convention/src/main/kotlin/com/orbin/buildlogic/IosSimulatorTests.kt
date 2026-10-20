package com.orbin.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

/** Use a fully booted simulator when tests need system services such as the real Keychain. */
internal fun Project.configureSimulatorTests() {
    val simulator = providers.gradleProperty("orbin.iosTestDevice")
    tasks.withType<KotlinNativeSimulatorTest>().configureEach {
        if (simulator.isPresent) {
            device.set(simulator)
            // Standalone mode does not start the system services used by Security.framework.
            standalone.set(false)
        }
    }
}
