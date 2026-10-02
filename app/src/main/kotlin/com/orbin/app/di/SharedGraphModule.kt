package com.orbin.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.data.database.OrbinDatabase
import com.orbin.data.settings.BoardPreferencesStore
import com.orbin.data.settings.SettingsStore
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.graph.SharedGraph
import com.orbin.graph.createSharedGraph
import com.orbin.provider.api.ImageBoardProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/**
 * Bridges the shared kotlin-inject graph (`:core:graph`, which iOS builds too) into Hilt.
 *
 * Hilt supplies the Android-only inputs — the OkHttp-backed client, the SQLCipher database, the
 * encrypted DataStore and the IO dispatcher — and re-exports what the graph builds from them, so
 * the sites, stores and repositories are constructed in one place for both platforms.
 */
@Module
@InstallIn(SingletonComponent::class)
object SharedGraphModule {
    @Provides
    @Singleton
    fun providesSharedGraph(
        client: HttpClient,
        database: OrbinDatabase,
        preferences: DataStore<Preferences>,
        @Dispatcher(OrbinDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): SharedGraph = createSharedGraph(client, database, preferences, ioDispatcher)

    @Provides
    fun providesJson(graph: SharedGraph): Json = graph.json

    @Provides
    fun providesSettingsStore(graph: SharedGraph): SettingsStore = graph.settingsStore

    @Provides
    fun providesBoardPreferencesStore(graph: SharedGraph): BoardPreferencesStore = graph.boardPreferences

    @Provides
    fun providesBookmarkRepository(graph: SharedGraph): BookmarkRepository = graph.bookmarks

    @Provides
    fun providesHistoryRepository(graph: SharedGraph): HistoryRepository = graph.history
    @Provides
    fun providesBoardRepository(graph: SharedGraph): com.orbin.domain.repository.BoardRepository = graph.boardRepository

    @Provides
    fun providesCatalogRepository(graph: SharedGraph): com.orbin.domain.repository.CatalogRepository = graph.catalogRepository

    @Provides
    fun providesThreadRepository(graph: SharedGraph): com.orbin.domain.repository.ThreadRepository = graph.threadRepository

    @Provides
    fun providesProviderRegistry(graph: SharedGraph): com.orbin.provider.api.ProviderRegistry = graph.providerRegistry

    @Provides
    @ElementsIntoSet
    fun providesImageBoardProviders(graph: SharedGraph): Set<ImageBoardProvider> = graph.providers
}
