package com.orbin.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [35])
class LegacyBrowsingCacheCleanupTest {
    @Test
    fun `startup retires all old caches without constructing media players`() {
        val context = RuntimeEnvironment.getApplication()
        val caches = listOf("http-cache", "image_cache", "media3-video-cache").map { context.cacheDir.resolve(it) }
        caches.forEach { directory ->
            directory.mkdirs()
            directory.resolve("private-url-and-content").writeText("sensitive")
        }
        context.openOrCreateDatabase("exoplayer_internal.db", 0, null).use {
            it.execSQL("CREATE TABLE legacy (url TEXT)")
        }
        val unrelated = context.cacheDir.resolve("unrelated").apply { writeText("keep") }

        retireLegacyBrowsingCaches(context)
        retireLegacyBrowsingCaches(context)

        caches.forEach { assertFalse(it.exists()) }
        assertFalse(context.getDatabasePath("exoplayer_internal.db").exists())
        assertTrue(unrelated.exists())
        unrelated.delete()
    }
}
