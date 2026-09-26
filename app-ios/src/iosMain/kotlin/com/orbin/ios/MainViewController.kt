package com.orbin.ios

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIViewController

/**
 * The app's root view controller, which the Swift side hosts full screen, built over [AppGraph]:
 * one HTTP client for the providers, image loading and saves, and one database for what the reader
 * keeps, shared with the background refresh.
 */
@Suppress("FunctionName", "unused") // Called from Swift as MainViewControllerKt.MainViewController().
fun MainViewController(): UIViewController {
    val graph = AppGraph
    val browser =
        Browser(
            providers = graph.providers,
            bookmarks = graph.bookmarks,
            history = graph.history,
            boardPreferences = graph.boardPreferences,
            settings = graph.settingsStore,
            scope = graph.scope,
            notifier = graph.notifier,
            onWatch = graph.notifier::requestPermission,
        )
    // Saving files from threads, and the Downloads tab's list, through the same client and database.
    val downloads = MediaDownloads(graph.downloadDao, DeviceMediaStore(), ktorMediaFetch(graph.client), graph.scope)
    // Export and import in Android's backup format, through the system share sheet and file picker.
    val backup =
        IosBackup(
            settings = graph.settingsStore,
            boardPreferences = graph.boardPreferences,
            bookmarks = graph.bookmarks,
            providers = graph.providers.map { it.metadata.id },
            files = DeviceBackupFiles(),
            scope = graph.scope,
            appVersion = graph.appVersion,
        )
    val lock =
        AppLock(
            graph.settingsStore.settings,
            graph.settingsStore::setBiometricLockEnabled,
            DeviceOwnerAuthenticator(),
            graph.scope,
        )
    // The app lives as long as this controller, so the observers are never removed.
    observe(UIApplicationDidBecomeActiveNotification) {
        lock.onForeground()
        browser.watched.refresh()
    }
    observe(UIApplicationWillResignActiveNotification) { lock.onResignActive() }
    observe(UIApplicationDidEnterBackgroundNotification) {
        lock.onBackground()
        // Leaving the app is when a background refresh is worth asking for.
        scheduleBackgroundRefresh()
    }
    return ComposeUIViewController {
        setSingletonImageLoaderFactory { context ->
            ImageLoader
                .Builder(context)
                .components { add(KtorNetworkFetcherFactory(httpClient = { graph.client })) }
                .build()
        }
        OrbinApp(remember { browser }, remember { lock }, remember { downloads }, remember { backup })
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
