package com.orbin.ios

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Bridge tests use an in-process handler and require no account, entitlement or CloudKit traffic. */
class NativeCloudSyncCancellationTest {
    @AfterTest
    fun clearHandler() = NativeCloudSync.clearHandlerForTesting()

    @Test
    fun cancelledRequestCancelsItsNativeTokenAndIgnoresLateCompletion() =
        runBlocking {
            var requestId = ""
            var cancelled = ""
            var complete: ((String) -> Unit)? = null
            NativeCloudSync.setHandler { operation, request, completion ->
                if (operation == "cancel") {
                    cancelled = request
                } else {
                    requestId =
                        Json
                            .parseToJsonElement(request)
                            .jsonObject
                            .getValue("requestId")
                            .jsonPrimitive.content
                    complete = completion
                }
            }
            val job = launch(start = CoroutineStart.UNDISPATCHED) { NativeCloudSync.request("availability") }
            job.cancelAndJoin()
            assertTrue(requestId.isNotEmpty())
            assertEquals(requestId, cancelled)
            complete?.invoke("""{"available":true}""")
            assertTrue(job.isCancelled)
        }

    @Test
    fun oldLeaseCleanupNeverUsesTheNewFetchSession() =
        runBlocking {
            val calls = mutableListOf<Pair<String, String>>()
            NativeCloudSync.setHandler { operation, request, completion ->
                val session =
                    Json
                        .parseToJsonElement(request)
                        .jsonObject
                        .getValue("sessionId")
                        .jsonPrimitive.content
                calls.add(operation to session)
                completion("""{"available":true}""")
            }
            val transport = DeviceCloudSyncTransport()
            val old = transport.newSession()
            val current = transport.newSession()
            old.fetch(emptyList())
            current.fetch(emptyList())
            old.releaseFetchedAssets()
            val firstSession = calls[0].second
            val currentSession = calls[1].second
            assertNotEquals(firstSession, currentSession)
            assertEquals("cleanup" to firstSession, calls[2])
            current.save(emptyList())
            assertEquals("save" to currentSession, calls[3])
        }
}
