package com.orbin.ios

import com.orbin.data.repository.BookmarkRepositoryImpl
import com.orbin.data.repository.HistoryRepositoryImpl
import com.orbin.data.settings.BoardPreferencesStore
import com.orbin.data.settings.SettingsStore
import com.orbin.provider.api.ViolentMediaCoverProvider
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import platform.Foundation.NSBundle

/**
 * Everything the app is built from, made once per process: the screens (`MainViewController`) and
 * the background refresh share it, so there is one database, one DataStore per file and one client
 * however the system launched the app. The background task can run with no screen made at all.
 */
internal object AppGraph {
    val scope = MainScope()

    /** One HTTP client for the providers, image loading and saves, so every request looks the same. */
    val client = orbinHttpClient(Darwin.create())

    private val database = openDatabase()

    // One DataStore per file: board preferences and settings share it, as they do on Android.
    private val preferences = openPreferences()

    val settingsStore = SettingsStore(preferences)
    val boardPreferences = BoardPreferencesStore(preferences)
    val bookmarks = BookmarkRepositoryImpl(database.bookmarkDao())
    val history = HistoryRepositoryImpl(database.historyDao())
    val downloadDao = database.downloadDao()

    /** The sites, with the same violent-media cover Android applies, following the same setting. */
    val providers =
        orbinProviders(client).map { provider ->
            ViolentMediaCoverProvider(provider) { settingsStore.settings.first().coverViolentMedia }
        }

    /** Posts new replies on watched threads, and asks to once the reader first watches one. */
    val notifier = IosThreadNotifier()

    /** "155" for 155-Ugli: the release number TestFlight shows as the version. */
    val appVersion: String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "ios"
}
