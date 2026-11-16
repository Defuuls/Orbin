package com.orbin.ios

import platform.Foundation.NSProcessInfo
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * Whether the native UI tests launched this process with the bundled board directory. Only a debug
 * framework (what `xcodebuild test` builds) honours the flag; release and TestFlight builds never do.
 */
@OptIn(ExperimentalNativeApi::class)
internal val bundledDirectoryLaunch: Boolean by lazy {
    val process = NSProcessInfo.processInfo
    Platform.isDebugBinary &&
        UiTestLaunchFlags.bundledDirectoryRequested(
            arguments = process.arguments.mapNotNull { it as? String },
            environment = stringEntries(process.environment),
        )
}

private fun stringEntries(environment: Map<Any?, *>): Map<String, String> =
    buildMap {
        environment.forEach { (key, value) ->
            if (key is String && value is String) put(key, value)
        }
    }
