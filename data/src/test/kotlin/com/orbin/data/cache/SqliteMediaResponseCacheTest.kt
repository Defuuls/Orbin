package com.orbin.data.cache

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import okio.buffer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SqliteMediaResponseCacheTest {
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var cache: SqliteMediaResponseCache

    @Before
    fun setUp() {
        val config =
            SupportSQLiteOpenHelper.Configuration
                .builder(ApplicationProvider.getApplicationContext())
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) = SqliteMediaResponseCache.createSchema(db)

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()
        helper = FrameworkSQLiteOpenHelperFactory().create(config)
        cache = SqliteMediaResponseCache({ helper.writableDatabase }, maxBytes = 16, maxEntryBytes = 12)
    }

    @After
    fun tearDown() {
        helper.close()
    }

    @Test
    fun `only committed complete media is readable`() {
        val writer = cache.start("https://cdn/image.gif", "image/gif", Long.MAX_VALUE)
        writer.append("GIF89a".toByteArray())
        assertThat(cache.open("https://cdn/image.gif", 0)).isNull()
        writer.commit()
        cache
            .open("https://cdn/image.gif", 0)!!
            .source
            .buffer()
            .use { assertThat(it.readUtf8()).isEqualTo("GIF89a") }
    }

    @Test
    fun `clear removes bytes and prevents an old writer from repopulating`() {
        val writer = cache.start("old", "image/gif", Long.MAX_VALUE)
        writer.append("GIF89a".toByteArray())
        cache.clear()
        assertThrows(IOException::class.java) { writer.commit() }
        writer.abort()
        assertThat(cache.usageBytes()).isEqualTo(0)
        assertThat(cache.open("old", 0)).isNull()
    }

    @Test
    fun `entry budget abort and expiration do not leave usable partial content`() {
        val writer = cache.start("large", "image/gif", Long.MAX_VALUE)
        assertThrows(IOException::class.java) { writer.append(ByteArray(13)) }
        assertThat(cache.usageBytes()).isEqualTo(0)
        val expired = cache.start("expired", "image/gif", 10)
        expired.append(byteArrayOf(1))
        expired.commit()
        assertThat(cache.open("expired", 11)).isNull()
        assertThat(cache.usageBytes()).isEqualTo(0)
    }

    @Test
    fun `quota evicts closed entries but protects active readers`() {
        fun save(key: String) {
            cache.start(key, "image/gif", Long.MAX_VALUE).apply {
                append(ByteArray(8))
                commit()
            }
        }
        save("a")
        val reading = cache.open("a", 0)!!
        save("b")
        save("c")
        assertThat(cache.open("b", 0)).isNull()
        reading.source.buffer().use { assertThat(it.readByteArray()).hasLength(8) }
        assertThat(cache.usageBytes()).isAtMost(16)
    }
}
