package com.orbin.ios

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Runs on the iOS simulator, against Security.framework rather than a fake secret store. */
class KeychainSyncPasswordStoreTest {
    @Test
    fun writesReadsReplacesAndClearsTheDeviceOnlyPassword() =
        runTest {
            val store = KeychainSyncPasswordStore(serviceName = "io.github.defuuls.orbin.tests.sync")
            try {
                store.write("päss:🔐")
                assertEquals("päss:🔐", store.read())
                store.write("replacement")
                assertEquals("replacement", store.read())
                store.write("")
                assertEquals("", store.read())
            } finally {
                store.write("")
            }
        }
}
