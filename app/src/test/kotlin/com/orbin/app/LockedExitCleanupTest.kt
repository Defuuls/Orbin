package com.orbin.app

import com.google.common.truth.Truth.assertThat
import com.orbin.domain.repository.VideoCacheRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LockedExitCleanupTest {
    @Test
    fun `leaving a locked app clears cached videos but keeps the one that is open`() =
        runTest {
            val cache = RecordingVideoCache()
            val cleanup = LockedExitCleanup({ cache }, this)

            cleanup.onLeavingApp(lockEnabled = true)
            advanceUntilIdle()

            assertThat(cache.calls).containsExactly("clearExceptOpenVideos")
        }

    @Test
    fun `leaving without App lock keeps the cache and never builds the video server`() =
        runTest {
            val cleanup = LockedExitCleanup({ error("video cache created") }, this)

            cleanup.onLeavingApp(lockEnabled = false)
            advanceUntilIdle()
        }

    @Test
    fun `a cache that cannot be cleared does not crash the exit and is tried again next time`() =
        runTest {
            val cache = RecordingVideoCache(failures = 1)
            val cleanup = LockedExitCleanup({ cache }, this)

            cleanup.onLeavingApp(lockEnabled = true)
            advanceUntilIdle()
            cleanup.onLeavingApp(lockEnabled = true)
            advanceUntilIdle()

            assertThat(cache.calls).containsExactly("clearExceptOpenVideos", "clearExceptOpenVideos")
            assertThat(cache.cleared).isEqualTo(1)
        }

    private class RecordingVideoCache(
        private var failures: Int = 0,
    ) : VideoCacheRepository {
        val calls = mutableListOf<String>()
        var cleared = 0

        override suspend fun usageBytes(): Long = 0L

        override suspend fun clear() {
            calls += "clear"
        }

        override suspend fun clearExceptOpenVideos() {
            calls += "clearExceptOpenVideos"
            check(failures-- <= 0) { "disk unavailable" }
            cleared++
        }

        override suspend fun setMaxCacheBytes(bytes: Long) {
            calls += "setMaxCacheBytes"
        }
    }
}
