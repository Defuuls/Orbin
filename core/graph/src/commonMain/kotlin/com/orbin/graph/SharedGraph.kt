package com.orbin.graph

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.orbin.data.archive.ThreadArchiver
import com.orbin.data.database.OrbinDatabase
import com.orbin.data.provider.ProviderRegistryImpl
import com.orbin.data.repository.BoardRepositoryImpl
import com.orbin.data.repository.BookmarkRepositoryImpl
import com.orbin.data.repository.CatalogRepositoryImpl
import com.orbin.data.repository.HistoryRepositoryImpl
import com.orbin.data.repository.RoomMediaLibraryRepository
import com.orbin.data.repository.RoomThreadMemoryRepository
import com.orbin.data.repository.SavedThreadRepositoryImpl
import com.orbin.data.repository.SearchRepositoryImpl
import com.orbin.data.repository.ThreadRepositoryImpl
import com.orbin.data.repository.UpdateRepositoryImpl
import com.orbin.data.settings.BoardPreferencesStore
import com.orbin.data.settings.SettingsRepositoryImpl
import com.orbin.data.settings.SettingsStore
import com.orbin.data.whatsnew.WhatsNewStore
import com.orbin.domain.repository.BoardPreferencesRepository
import com.orbin.domain.repository.BoardRepository
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.CatalogRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.MediaLibraryRepository
import com.orbin.domain.repository.SavedThreadRepository
import com.orbin.domain.repository.SearchRepository
import com.orbin.domain.repository.SessionHistoryRepository
import com.orbin.domain.repository.SessionThreadMemoryRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.repository.ThreadMemoryRepository
import com.orbin.domain.repository.ThreadRepository
import com.orbin.domain.repository.UpdateRepository
import com.orbin.domain.usecase.BuildReplyGraphUseCase
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.InMemoryProviderDiagnostics
import com.orbin.provider.api.ProviderDiagnostics
import com.orbin.provider.api.ProviderRegistry
import com.orbin.provider.lynxchan.LynxChanProvider
import com.orbin.provider.lynxchan.LynxChanSite
import com.orbin.provider.lynxchan.api.KtorLynxChanApi
import com.orbin.provider.vichan.VichanProvider
import com.orbin.provider.vichan.VichanSite
import com.orbin.provider.vichan.api.KtorVichanApi
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.KmpComponentCreate
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.annotations.Scope
import okio.Path.Companion.toPath

/** One instance per [SharedGraph], which each platform makes once per process. */
@Scope
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class AppScope

/** The lenient parser the providers expect. Android and iOS both read through this one. */
val OrbinJson: Json =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

/**
 * What Android and iOS build the app from, wired once at compile time.
 *
 * The constructor takes what only a platform can make: the [HttpClient] (OkHttp engine on Android,
 * Darwin on iOS), the opened [OrbinDatabase] (encrypted on Android), the settings [DataStore] and
 * the dispatcher provider work runs on. Everything else is built here, the same way on both: the
 * sites, the settings stores and the database-backed repositories. A missing or duplicate binding
 * fails the build rather than the first launch.
 *
 * Create it with [createSharedGraph].
 */
@AppScope
@Component
abstract class SharedGraph(
    @get:Provides protected val client: HttpClient,
    @get:Provides protected val database: OrbinDatabase,
    @get:Provides protected val preferences: DataStore<Preferences>,
    @get:Provides protected val dispatcher: CoroutineDispatcher,
    @get:Provides protected val offlineDirectory: String? = null,
) {
    private val repositoryLifetime = RepositoryLifetime(dispatcher)

    /** Stop and await this graph's repository jobs before its caller closes the database. */
    suspend fun shutdown() = repositoryLifetime.shutdown()

    @Provides
    protected fun repositoryScope(): CoroutineScope = repositoryLifetime.scope

    abstract val json: Json
    abstract val settingsStore: SettingsStore
    abstract val boardPreferences: BoardPreferencesStore
    abstract val bookmarks: BookmarkRepository
    abstract val history: HistoryRepository
    abstract val providers: Set<ImageBoardProvider>
    abstract val providerDiagnostics: ProviderDiagnostics

    abstract val boards: BoardRepository
    abstract val catalog: CatalogRepository
    abstract val threads: ThreadRepository
    abstract val threadMemory: ThreadMemoryRepository
    abstract val mediaLibrary: MediaLibraryRepository
    abstract val savedThreads: SavedThreadRepository
    abstract val search: SearchRepository
    abstract val settings: SettingsRepository
    abstract val boardPreferencesRepository: BoardPreferencesRepository
    abstract val providerRegistry: ProviderRegistry
    abstract val updates: UpdateRepository
    abstract val whatsNew: WhatsNewStore
    abstract val threadArchive: ThreadArchiver

    @Provides
    protected fun json(): Json = OrbinJson

    // Settings and board preferences share one DataStore file, as they always have.
    @AppScope
    @Provides
    protected fun settingsStore(preferences: DataStore<Preferences>): SettingsStore = SettingsStore(preferences)

    @AppScope
    @Provides
    protected fun boardPreferences(preferences: DataStore<Preferences>): BoardPreferencesStore =
        BoardPreferencesStore(preferences)

    @AppScope
    @Provides
    protected fun bookmarks(database: OrbinDatabase): BookmarkRepository =
        BookmarkRepositoryImpl(database.bookmarkDao())

    @AppScope
    @Provides
    protected fun history(
        database: OrbinDatabase,
        settings: SettingsRepository,
        scope: CoroutineScope,
    ): HistoryRepository =
        SessionHistoryRepository(HistoryRepositoryImpl(database.historyDao()), settings.settings, scope)

    @AppScope
    @Provides
    protected fun providerDiagnostics(): ProviderDiagnostics = InMemoryProviderDiagnostics()

    @AppScope
    @Provides
    protected fun settingsRepositoryImpl(
        settingsStore: SettingsStore,
        boardPreferences: BoardPreferencesStore,
    ): SettingsRepositoryImpl = SettingsRepositoryImpl(settingsStore, boardPreferences)

    @Provides
    protected fun settingsRepository(impl: SettingsRepositoryImpl): SettingsRepository = impl

    @Provides
    protected fun boardPreferencesRepository(impl: SettingsRepositoryImpl): BoardPreferencesRepository = impl

    @AppScope
    @Provides
    protected fun providerRegistry(
        providers: Set<ImageBoardProvider>,
        diagnostics: ProviderDiagnostics,
        settings: SettingsRepository,
    ): ProviderRegistry = ProviderRegistryImpl(providers, diagnostics, settings)

    @AppScope
    @Provides
    protected fun boardRepository(
        registry: ProviderRegistry,
        database: OrbinDatabase,
        dispatcher: CoroutineDispatcher,
    ): BoardRepository = BoardRepositoryImpl(registry, database.boardDao(), dispatcher)

    @AppScope
    @Provides
    protected fun catalogRepository(registry: ProviderRegistry): CatalogRepository = CatalogRepositoryImpl(registry)

    @AppScope
    @Provides
    protected fun threadRepository(
        registry: ProviderRegistry,
        dispatcher: CoroutineDispatcher,
        savedThreads: SavedThreadRepository,
    ): ThreadRepository = ThreadRepositoryImpl(registry, BuildReplyGraphUseCase(), dispatcher, savedThreads)

    @AppScope
    @Provides
    @Suppress("TooGenericExceptionCaught") // Optional cache pruning tolerates platform database failures.
    protected fun savedThreadRepository(
        database: OrbinDatabase,
        dispatcher: CoroutineDispatcher,
        offlineDirectory: String?,
        bookmarks: BookmarkRepository,
        history: HistoryRepository,
        settings: SettingsRepository,
        scope: CoroutineScope,
    ): SavedThreadRepository {
        val repository =
            SavedThreadRepositoryImpl(
                database.savedThreadDao(),
                dispatcher,
                offlineDirectory?.toPath(),
                automaticCopyDao = database.offlineThreadCopyDao(),
                canRetainAutomaticCopy = { key ->
                    bookmarks.getBookmark(key) != null ||
                        (!settings.settings.first().historySessionOnly && history.getEntry(key) != null)
                },
            )
        run {
            scope.launch {
                combine(
                    bookmarks.observeBookmarks(),
                    history.observeVisitedKeys(),
                    settings.settings,
                ) { saved, visited, options ->
                    saved.mapTo(mutableSetOf()) { it.key }.apply {
                        if (!options.historySessionOnly) addAll(visited)
                    }
                }.distinctUntilChanged().collect { retained ->
                    try {
                        repository.pruneAutomaticCopies(retained)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // The next collection update retries cleanup; foreground repositories remain available.
                    }
                }
            }
        }
        return repository
    }

    @AppScope
    @Provides
    protected fun threadMemory(
        database: OrbinDatabase,
        settings: SettingsRepository,
    ): ThreadMemoryRepository =
        SessionThreadMemoryRepository(RoomThreadMemoryRepository(database.threadMemoryDao()), settings.settings)

    @AppScope
    @Provides
    protected fun mediaLibrary(database: OrbinDatabase): MediaLibraryRepository =
        RoomMediaLibraryRepository(database.mediaLibraryDao())

    @AppScope
    @Provides
    protected fun searchRepository(
        registry: ProviderRegistry,
        database: OrbinDatabase,
        dispatcher: CoroutineDispatcher,
    ): SearchRepository =
        SearchRepositoryImpl(registry, database.recentSearchDao(), database.savedSearchDao(), dispatcher)

    @AppScope
    @Provides
    protected fun updateRepository(
        client: HttpClient,
        dispatcher: CoroutineDispatcher,
    ): UpdateRepository = UpdateRepositoryImpl(client, dispatcher)

    // Whole threads saved as a page with their files, for keeps.
    @AppScope
    @Provides
    protected fun threadArchive(
        client: HttpClient,
        dispatcher: CoroutineDispatcher,
    ): ThreadArchiver = ThreadArchiver(client, dispatcher)

    @AppScope
    @Provides
    protected fun whatsNew(preferences: DataStore<Preferences>): WhatsNewStore = WhatsNewStore(preferences)

    @AppScope
    @Provides
    protected fun providers(
        client: HttpClient,
        json: Json,
        dispatcher: CoroutineDispatcher,
    ): Set<ImageBoardProvider> = orbinProviders(client, json, dispatcher).toSet()
}

/**
 * The sites the app reads, in display order. To add a site, add it here; Android and iOS both
 * pick it up. Usable without the graph (tests build providers over a mock engine).
 */
fun orbinProviders(
    client: HttpClient,
    json: Json = OrbinJson,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): List<ImageBoardProvider> {
    val vichan = VichanSite.Example
    val lynxChan = LynxChanSite.BbwChan
    return listOf(
        VichanProvider(vichan, KtorVichanApi(client, vichan.apiBaseUrl, json), dispatcher),
        LynxChanProvider(lynxChan, KtorLynxChanApi(client, lynxChan.apiBaseUrl, json), dispatcher),
    )
}

/** Makes the graph; kotlin-inject generates the `actual` for each target. */
@KmpComponentCreate
expect fun createSharedGraph(
    client: HttpClient,
    database: OrbinDatabase,
    preferences: DataStore<Preferences>,
    dispatcher: CoroutineDispatcher,
    offlineDirectory: String? = null,
): SharedGraph
