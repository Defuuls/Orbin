package com.orbin.ios

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.orbin.core.model.FormFactor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.UIViewController
import platform.UserNotifications.UNUserNotificationCenter

/**
 * The app's root view controller, which the Swift side hosts full screen, built over [AppGraph]:
 * one HTTP client for the providers, image loading and saves, and one database for what the reader
 * keeps, shared with the background refresh.
 */
@OptIn(ExperimentalForeignApi::class)
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
            downloadDao = graph.downloadDao,
            onWatch = graph.notifier::requestPermission,
        )
    // Saving files from threads, and the Downloads tab's list, through the same client and database.
    val downloads =
        MediaDownloads(
            graph.downloadDao,
            DeviceMediaStore(),
            ktorMediaFetch(graph.client),
            graph.scope,
            durableFetch = IosBackgroundMediaFetch,
        )
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
    graph.notifier.attachNavigation(browser::openThread)

    // The app lives as long as this controller, so the observers are never removed.
    observe(UIApplicationDidBecomeActiveNotification) {
        lock.onForeground()
        browser.watched.refresh()
        UNUserNotificationCenter.currentNotificationCenter().setBadgeCount(0, withCompletionHandler = null)
        // Pick up what was read on another device since.
        graph.scope.launch { graph.threadSync.sync() }
    }
    observe(UIApplicationWillResignActiveNotification) { lock.onResignActive() }
    observe(UIApplicationDidEnterBackgroundNotification) {
        lock.onBackground()
        // Leaving the app is when a background refresh is worth asking for.
        scheduleBackgroundRefresh()
        // And when what was read here should reach the other devices.
        graph.scope.launch { graph.threadSync.sync() }
    }
    return ComposeUIViewController {
        setSingletonImageLoaderFactory { context -> imageLoader(context, graph) }
        OrbinApp(
            remember { browser },
            remember { lock },
            remember { downloads },
            remember { backup },
            formFactor(),
            AppVersion(graph.appVersion, graph.appBuild),
            graph.threadSync,
        )
    }.also { controller -> attachPencil(controller.view) }
}

/** Coil over the app's Ktor client (encrypted DNS included), with its disk cache in Caches. */
@OptIn(ExperimentalForeignApi::class)
private fun imageLoader(
    context: PlatformContext,
    graph: AppGraph,
): ImageLoader {
    val cacheDirectory =
        NSFileManager
            .defaultManager
            .URLForDirectory(
                directory = NSCachesDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            )?.path ?: ""
    return ImageLoader
        .Builder(context)
        .components { add(KtorNetworkFetcherFactory(httpClient = { graph.client })) }
        .diskCache {
            DiskCache
                .Builder()
                .directory("$cacheDirectory/image_cache".toPath())
                .maxSizeBytes(COIL_DISK_CACHE_BYTES)
                .build()
        }.crossfade(false)
        .build()
}

private const val COIL_DISK_CACHE_BYTES: Long = 250L * 1024 * 1024

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

/** An iPad is a tablet, with Android's tablet column choice; everything else is a phone. */
private fun formFactor(): FormFactor =
    if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) FormFactor.TABLET else FormFactor.PHONE
