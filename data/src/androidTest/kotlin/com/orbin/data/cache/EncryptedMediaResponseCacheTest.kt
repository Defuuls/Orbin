package com.orbin.data.cache

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import okio.buffer
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise real SQLCipher: Robolectric cannot verify its native encryption boundary. */
@RunWith(AndroidJUnit4::class)
class EncryptedMediaResponseCacheTest {
    @Test
    fun cachedGifAndUrlAreEncryptedAndSurviveReopening() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "encrypted-media-cache-test.db"
        val key = ByteArray(32) { (it + 1).toByte() }
        val url = "https://private-media.example/private-thread-123.gif"
        val contents = "GIF89a-private-animated-content-not-readable-in-a-file-copy"
        System.loadLibrary("sqlcipher")
        context.deleteDatabase(name)

        fun open(): SupportSQLiteOpenHelper =
            SupportOpenHelperFactory(key).create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(1) {
                            override fun onCreate(db: SupportSQLiteDatabase) = SqliteMediaResponseCache.createSchema(db)

                            override fun onUpgrade(
                                db: SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        },
                    ).build(),
            )
        try {
            open().use { helper ->
                SqliteMediaResponseCache({ helper.writableDatabase })
                    .start(url, "image/gif", Long.MAX_VALUE)
                    .apply {
                        append(contents.toByteArray())
                        commit()
                    }
            }
            val raw = context.getDatabasePath(name).readBytes().toString(Charsets.ISO_8859_1)
            assertThat(raw).doesNotContain("SQLite format 3")
            assertThat(raw).doesNotContain(url)
            assertThat(raw).doesNotContain(contents)
            open().use { helper ->
                val cache = SqliteMediaResponseCache({ helper.writableDatabase })
                cache
                    .open(url, 0)!!
                    .source
                    .buffer()
                    .use { assertThat(it.readUtf8()).isEqualTo(contents) }
                cache.clear()
                assertThat(cache.open(url, 0)).isNull()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
