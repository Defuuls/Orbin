package com.orbin.graph

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.orbin.data.database.OrbinDatabase
import com.orbin.data.repository.BookmarkRepositoryImpl
import com.orbin.data.repository.HistoryRepositoryImpl
import com.orbin.data.settings.BoardPreferencesStore
import com.orbin.data.settings.SettingsStore
import com.orbin.domain.repository.BookmarkRepository
import com.orbin.domain.repository.HistoryRepository
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.lynxchan.LynxChanProvider
import com.orbin.provider.lynxchan.LynxChanSite
import com.orbin.provider.lynxchan.api.KtorLynxChanApi
import com.orbin.provider.vichan.VichanProvider
import com.orbin.provider.vichan.VichanSite
import com.orbin.provider.vichan.api.KtorVichanApi
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.KmpComponentCreate
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.annotations.Scope

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
) {
    abstract val json: Json
    abstract val settingsStore: SettingsStore
    abstract val boardPreferences: BoardPreferencesStore
    abstract val bookmarks: BookmarkRepository
    abstract val history: HistoryRepository

    /**
     * The sites the app reads, undecorated. Each platform wraps them in the violent-media cover,
     * which follows a setting it reads its own way.
     */
    abstract val providers: Set<ImageBoardProvider>

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
    protected fun history(database: OrbinDatabase): HistoryRepository = HistoryRepositoryImpl(database.historyDao())

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
): SharedGraph
