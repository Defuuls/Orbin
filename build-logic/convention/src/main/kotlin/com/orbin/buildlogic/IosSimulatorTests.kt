package com.orbin.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

/**
 * Use a fully booted simulator when tests need system services such as the real Keychain.
 *
 * The device id is wired as a task-input Provider so it is not a configuration-cache key.
 * Prefer the `ORBIN_IOS_TEST_DEVICE` environment variable (what CI sets). The
 * `orbin.iosTestDevice` Gradle property still works for local runs.
 *
 * The fallback must be a constant. Looking up a simulator with `xcrun` from a Provider
 * starts an external process at configuration time and fails the configuration cache
 * even after the suites have passed.
 */
internal fun Project.configureSimulatorTests() {
    val requestedDevice = providers.environmentVariable("ORBIN_IOS_TEST_DEVICE")
        .orElse(providers.gradleProperty("orbin.iosTestDevice"))
        .map(String::trim)
        .filter { it.isNotEmpty() }

    tasks.withType<KotlinNativeSimulatorTest>().configureEach {
        // device is a required @Input and is finalized before doFirst, so it must be a
        // Provider set at configuration time. When no override is present, standalone
        // mode uses a placeholder name; set the env var or property to pick a real device.
        standalone.set(requestedDevice.map { false }.orElse(true))
        device.set(requestedDevice.orElse("iPhone"))
    }
}
