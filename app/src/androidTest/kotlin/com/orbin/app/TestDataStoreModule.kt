package com.orbin.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.orbin.data.di.DataStoreModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import java.util.UUID
import javax.inject.Singleton

/**
 * Gives each test its own settings store.
 *
 * `@HiltAndroidTest` builds a fresh component per test, all in one process, so the real module's
 * singleton DataStore is constructed repeatedly over the same file — which DataStore rejects
 * outright ("multiple DataStores active for the same file"), taking the activity down with it.
 * A unique file per component sidesteps that, and also starts every test from settings that are
 * genuinely empty rather than whatever the previous one left behind.
 *
 * Unencrypted on purpose: the encrypted serializer's value is the Keystore, which is not what an
 * app-launch smoke test is checking.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataStoreModule::class])
object TestDataStoreModule {
    @Provides
    @Singleton
    fun providesPreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = {
                File(context.filesDir, "datastore-test")
                    .apply { mkdirs() }
                    .resolve("settings_${UUID.randomUUID()}.preferences_pb")
            },
        )
}
