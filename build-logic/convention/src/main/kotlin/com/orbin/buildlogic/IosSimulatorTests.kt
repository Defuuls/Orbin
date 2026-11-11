package com.orbin.buildlogic

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

/**
 * Use a fully booted simulator when tests need system services such as the real Keychain.
 *
 * The device id is wired as a task-input Provider so it is not a configuration-cache key.
 * Prefer the `ORBIN_IOS_TEST_DEVICE` environment variable (what CI sets). The
 * `orbin.iosTestDevice` Gradle property still works for local runs.
 */
internal fun Project.configureSimulatorTests() {
    val requestedDevice = providers.environmentVariable("ORBIN_IOS_TEST_DEVICE")
        .orElse(providers.gradleProperty("orbin.iosTestDevice"))
        .map(String::trim)
        .filter { it.isNotEmpty() }

    tasks.withType<KotlinNativeSimulatorTest>().configureEach {
        // device is a required @Input and is finalized before doFirst, so it must be a
        // Provider set at configuration time. When no override is present, fall back to an
        // available iPhone on macOS (or a dummy name on other hosts, where these tasks are
        // disabled and never run).
        standalone.set(requestedDevice.map { false }.orElse(true))
        device.set(requestedDevice.orElse(defaultSimulatorDevice()))
    }
}

private fun Project.defaultSimulatorDevice(): Provider<String> =
    providers.provider {
        if (!System.getProperty("os.name").orEmpty().startsWith("Mac")) {
            return@provider "iPhone"
        }
        val listing = ProcessBuilder("xcrun", "simctl", "list", "devices", "available")
            .redirectErrorStream(true)
            .start()
            .inputStream
            .bufferedReader()
            .readText()
        val match = Regex("""^\s+(iPhone [^(]+?)\s+\([0-9A-F-]{36}\)""", RegexOption.MULTILINE)
            .find(listing)
        match?.groupValues?.get(1)?.trim() ?: "iPhone"
    }
