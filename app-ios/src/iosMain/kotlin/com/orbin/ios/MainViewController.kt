package com.orbin.ios

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.orbin.data.repository.BookmarkRepositoryImpl
import com.orbin.data.repository.HistoryRepositoryImpl
import com.orbin.data.settings.BoardPreferencesStore
import com.orbin.data.settings.SettingsStore
import com.orbin.provider.api.ViolentMediaCoverProvider
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIViewController

/**
 * The app's root view controller, which the Swift side hosts full screen. One HTTP client serves
 * both the providers and image loading, so every request carries the same headers; one database
 * holds what the reader keeps.
 */
@Suppress("FunctionName", "unused") // Called from Swift as MainViewControllerKt.MainViewController().
fun MainViewController(): UIViewController {
    val client = orbinHttpClient(Darwin.create())
    val database = openDatabase()
    // One DataStore per file: board preferences and settings share it, as they do on Android.
    val preferences = openPreferences()
    val settingsStore = SettingsStore(preferences)
    val scope = MainScope()
    val browser =
        Browser(
            // The same violent-media cover Android applies, following the same setting.
            providers =
                orbinProviders(client).map { provider ->
                    ViolentMediaCoverProvider(provider) { settingsStore.settings.first().coverViolentMedia }
                },
            bookmarks = BookmarkRepositoryImpl(database.bookmarkDao()),
            history = HistoryRepositoryImpl(database.historyDao()),
            boardPreferences = BoardPreferencesStore(preferences),
            settings = settingsStore,
            scope = scope,
        )
    val lock =
        AppLock(settingsStore.settings, settingsStore::setBiometricLockEnabled, DeviceOwnerAuthenticator(), scope)
    // The app lives as long as this controller, so the observers are never removed.
    observe(UIApplicationDidBecomeActiveNotification) {
        lock.onForeground()
        browser.watched.refresh()
    }
    observe(UIApplicationWillResignActiveNotification) { lock.onResignActive() }
    observe(UIApplicationDidEnterBackgroundNotification) { lock.onBackground() }
    return ComposeUIViewController {
        setSingletonImageLoaderFactory { context ->
            ImageLoader
                .Builder(context)
                .components { add(KtorNetworkFetcherFactory(httpClient = { client })) }
                .build()
        }
        OrbinApp(remember { browser }, remember { lock })
    }
}

private fun observe(
    name: String?,
    action: () -> Unit,
) {
    NSNotificationCenter.defaultCenter.addObserverForName(
        name,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { _ ->
        action()
    }
}
