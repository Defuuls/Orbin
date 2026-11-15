@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.orbin.ios

import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIDevice
import kotlin.test.assertTrue

/** A reported test pass cannot stand in for a decode branch that the runtime never entered. */
internal fun requireWebMDecodeRuntime(caseName: String): Boolean {
    val process = NSProcessInfo.processInfo
    val device = UIDevice.currentDevice
    val version = process.operatingSystemVersion.useContents { Triple(majorVersion, minorVersion, patchVersion) }
    val modernRuntime =
        version.first > WEBM_MIN_MAJOR ||
            version.first == WEBM_MIN_MAJOR &&
            version.second >= WEBM_MIN_MINOR
    val environment = process.environment
    val requiredByCi =
        environment["ORBIN_REQUIRE_WEBM_DECODE"] == "1" ||
            environment["SIMCTL_CHILD_ORBIN_REQUIRE_WEBM_DECODE"] == "1"
    val supported = supportsWebM
    val diagnostics =
        listOf(
            "case=$caseName",
            "systemName=${device.systemName}",
            "systemVersion=${device.systemVersion}",
            "model=${device.model}",
            "processVersion=${version.first}.${version.second}.${version.third}",
            "processVersionString=${process.operatingSystemVersionString}",
            "supportsWebM=$supported",
            "requiredByCi=$requiredByCi",
            "simulatorName=${environment["SIMULATOR_DEVICE_NAME"]}",
            "simulatorUDID=${environment["SIMULATOR_UDID"]}",
            "simulatorRuntime=${environment["SIMULATOR_RUNTIME_VERSION"]}",
            "selectedRuntime=${environment["ORBIN_IOS_TEST_RUNTIME"]}",
        ).joinToString(" | ")
    println("WEBM_RUNTIME $diagnostics")
    if (requiredByCi) assertTrue(modernRuntime, "CI must run WebM tests on iOS 17.4 or later: $diagnostics")
    if (requiredByCi || modernRuntime) {
        assertTrue(supported, "A required modern runtime must enter the real WebM decode assertions: $diagnostics")
    }
    if (!supported) {
        println("WEBM_NOT_EXERCISED $diagnostics | reason=unsupported OS; no decode validation")
        return false
    }
    println("WEBM_DECODE_STARTED case=$caseName")
    return true
}

/** Call only after this case's original assertions and resource teardown have all succeeded. */
internal fun verifiedWebMDecodeCase(caseName: String) {
    println("WEBM_DECODE_VERIFIED case=$caseName")
}

private const val WEBM_MIN_MAJOR = 17L
private const val WEBM_MIN_MINOR = 4L
