package com.orbin.ios

import com.orbin.data.sync.IosNearbyBluetooth
import com.orbin.data.sync.MigratingSyncPasswordStore
import com.orbin.data.sync.NearbySyncSecrets
import com.orbin.graph.createSharedGraph
import com.orbin.provider.api.ViolentMediaCoverProvider
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSUserDomainMask

/**
 * Everything the app is built from, made once per process: the screens (`MainViewController`) and
 * the background refresh share it, so there is one database, one DataStore per file and one client
 * however the system launched the app. The background task can run with no screen made at all.
 */
internal object AppGraph {
    val scope = MainScope()

    /**
     * One HTTP client for the providers, image loading and saves, so every request looks the same.
     * Catalogs, threads and board lists always come from the site: iOS's URL cache otherwise served
     * them for up to a minute, so pulling to refresh showed the same page again. Media keeps normal
     * HTTP caching.
     */
    val client =
        iosHttpClient(
            Darwin.create {
                configureRequest {
                    if (!isMediaUrl(URL?.path.orEmpty())) setCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
                }
            },
        )

    private val database = openDatabase()

    /**
     * The sites, stores and repositories, built by the same kotlin-inject graph Android uses. One
     * DataStore file holds both settings and board preferences, as on Android.
     */
    private val preferences = openPreferences()
    private val shared =
        createSharedGraph(
            client,
            database,
            preferences,
            Dispatchers.Default,
            MigratingSyncPasswordStore(preferences, KeychainSyncPasswordStore()),
            NearbySyncSecrets(
                KeychainSyncPasswordStore(serviceName = "io.github.defuuls.orbin.nearby"),
                IosNearbyBluetooth(),
            ),
        )

    val settingsStore = shared.settingsStore
    val boardPreferences = shared.boardPreferences
    val bookmarks = shared.bookmarks
    val history = shared.history

    /** Thread progress kept in step with the reader's other devices through their WebDAV folder. */
    val threadSync = shared.threadSync
    val nearbySync = shared.nearbySync
    val whatsNew = shared.whatsNew

    // Whole threads saved as pages go beside saved files, in Orbin's folder in the Files app.
    val pages =
        ThreadPages(
            shared.threadArchive,
            (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String) +
                "/Orbin/Threads",
        )
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

private val MEDIA_EXTENSIONS =
    setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "bmp", "webm", "mp4", "m4v", "mov", "mp3", "ogg", "m4a", "wav")

/** A picture, video or sound file, which may come from the cache; anything else is page data. */
internal fun isMediaUrl(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in MEDIA_EXTENSIONS
