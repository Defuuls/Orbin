package com.orbin.graph

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryLifetimeTest {
    @Test
    fun shutdownWaitsForEveryObserverBeforeTheCallerCanCloseItsDatabase() =
        runTest {
            val lifetime = RepositoryLifetime(StandardTestDispatcher(testScheduler))
            val release = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            repeat(2) { observer ->
                lifetime.scope.launch {
                    try {
                        events += "started $observer"
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            release.await()
                            events += "stopped $observer"
                        }
                    }
                }
            }
            runCurrent()
            val shutdown =
                backgroundScope.launch {
                    lifetime.shutdown()
                    events += "database may close"
                }
            runCurrent()
            assertFalse(shutdown.isCompleted, "Database ownership cannot return while observers still clean up")
            release.complete(Unit)
            shutdown.join()
            assertEquals(2, events.count { it.startsWith("stopped") })
            assertEquals("database may close", events.last())
            assertTrue(
                coroutineContext[kotlinx.coroutines.Job]!!.isActive,
                "Shutting down a graph must not cancel its caller",
            )
        }
}
