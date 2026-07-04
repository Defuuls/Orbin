package com.orbin.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.orbin.data.crypto.EncryptedPreferencesSerializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/** Provides the singleton DataStore used for app settings, encrypted at rest. */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun providesPreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        DataStoreFactory.create(
            serializer = EncryptedPreferencesSerializer,
            // If the encrypted file can't be read (e.g. the Keystore key was lost after a device
            // restore), reset to defaults instead of crash-looping the app.
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = {
                File(context.filesDir, "datastore").apply { mkdirs() }.resolve(SETTINGS_FILE_NAME)
            },
        )

    private const val SETTINGS_FILE_NAME = "orbin_settings_enc.preferences"
}
