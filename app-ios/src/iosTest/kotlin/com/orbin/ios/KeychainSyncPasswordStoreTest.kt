package com.orbin.ios

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/** Runs on the iOS simulator, against Security.framework rather than a fake secret store. */
class KeychainSyncPasswordStoreTest {
    @Test
    fun writesReadsReplacesAndClearsTheDeviceOnlyPassword() =
        runTest {
            val store = KeychainSyncPasswordStore(serviceName = "io.github.defuuls.orbin.tests.sync")
            try {
                // Gradle's report drops the exception message; fail with it so the
                // Keychain status code is visible in CI.
                runCatching { store.write("päss:🔐") }.onFailure { fail("Keychain write failed: ${it.message}", it) }
                assertEquals("päss:🔐", store.read())
                store.write("replacement")
                assertEquals("replacement", store.read())
                store.write("")
                assertEquals("", store.read())
            } finally {
                runCatching { store.write("") }
            }
        }
}
