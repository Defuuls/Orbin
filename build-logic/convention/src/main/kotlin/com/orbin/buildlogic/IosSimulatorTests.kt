package com.orbin.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

/**
 * Use a fully booted simulator when tests need system services such as the real Keychain.
 *
 * The device id is read at task execution so it is not a configuration-cache key. Prefer the
 * `ORBIN_IOS_TEST_DEVICE` environment variable (what CI sets). The `orbin.iosTestDevice`
 * Gradle property still works for local runs.
 */
internal fun Project.configureSimulatorTests() {
    val requestedDevice = providers.environmentVariable("ORBIN_IOS_TEST_DEVICE")
        .orElse(providers.gradleProperty("orbin.iosTestDevice"))
        .map(String::trim)
        .filter { it.isNotEmpty() }

    tasks.withType<KotlinNativeSimulatorTest>().configureEach {
        // Wire the provider as a task input so the configuration cache can be reused across
        // different simulator UDIDs. Apply it in doFirst so Kotlin's default device name is
        // left alone when no override is present (device is a required @Input, not optional).
        inputs.property("orbinIosTestDevice", requestedDevice).optional(true)
        doFirst {
            val id = requestedDevice.orNull
            if (!id.isNullOrEmpty()) {
                device.set(id)
                standalone.set(false)
            }
        }
    }
}
