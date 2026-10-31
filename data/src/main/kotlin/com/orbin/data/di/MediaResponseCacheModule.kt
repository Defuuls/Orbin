package com.orbin.data.di

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.orbin.data.cache.SqliteMediaResponseCache
import com.orbin.data.crypto.DatabasePassphrase
import com.orbin.network.MediaResponseCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaResponseCacheModule {
    @Provides
    @Singleton
    fun providesMediaResponseCache(
        @ApplicationContext context: Context,
    ): MediaResponseCache {
        val helper by lazy {
            System.loadLibrary("sqlcipher")
            val resolved = DatabasePassphrase(context).resolve()
            if (resolved.wipeDatabase) context.deleteDatabase(SqliteMediaResponseCache.NAME)
            val configuration =
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    // No name is an in-memory database: without a saved key, nothing goes to disk.
                    .name(SqliteMediaResponseCache.NAME.takeIf { resolved.persisted })
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(1) {
                            override fun onCreate(db: SupportSQLiteDatabase) = SqliteMediaResponseCache.createSchema(db)

                            override fun onUpgrade(
                                db: SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit

                            override fun onOpen(db: SupportSQLiteDatabase) {
                                // An interrupted download never becomes a cache hit after process death.
                                db.execSQL(
                                    "DELETE FROM media_chunks WHERE token IN (SELECT token FROM media_entries WHERE complete = 0)",
                                )
                                db.execSQL("DELETE FROM media_entries WHERE complete = 0")
                            }
                        },
                    ).build()
            SupportOpenHelperFactory(resolved.passphrase).create(configuration)
        }
        return SqliteMediaResponseCache({ helper.writableDatabase })
    }
}
