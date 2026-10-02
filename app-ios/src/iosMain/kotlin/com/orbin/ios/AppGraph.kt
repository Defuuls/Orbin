package com.orbin.ios

import com.orbin.graph.createSharedGraph
import com.orbin.provider.api.ViolentMediaCoverProvider
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.Dispatchers
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

    /**
     * The sites, stores and repositories, built by the same kotlin-inject graph Android uses. One
     * DataStore file holds both settings and board preferences, as on Android.
     */
    private val shared = createSharedGraph(client, database, openPreferences(), Dispatchers.Default)

    val settingsStore = shared.settingsStore
    val boardPreferences = shared.boardPreferences
    val bookmarks = shared.bookmarks
    val history = shared.history
    val downloadDao = database.downloadDao()

    /** The sites, with the same violent-media cover Android applies, following the same setting. */
    val providers =
        shared.providers.map { provider ->
            ViolentMediaCoverProvider(provider) { settingsStore.settings.first().coverViolentMedia }
        }

    /** Posts new replies on watched threads, and asks to once the reader first watches one. */
    val notifier = IosThreadNotifier()

    /**
     * The Android release this build was cut from, "158-Yuzu", which TestFlight's numbers-only
     * version cannot hold; the build writes it into Info.plist as OrbinVersionName. A build made in
     * Xcode without it falls back to the numeric version, "158".
     */
    val appVersion: String =
        infoString("OrbinVersionName")?.takeUnless { it.isBlank() }
            ?: infoString("CFBundleShortVersionString")
            ?: "ios"

    /** TestFlight's build number, "176.202609262351": the version code and the upload time. */
    val appBuild: String = infoString("CFBundleVersion").orEmpty()

    private fun infoString(key: String): String? = NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String
}
