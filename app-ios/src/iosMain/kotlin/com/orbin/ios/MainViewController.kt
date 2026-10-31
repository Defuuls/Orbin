package com.orbin.ios

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.orbin.core.model.FormFactor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    IosStartupCrashes.install()
    // Decided before anything reads local state: a crash loop most likely comes from that state,
    // so safe mode must not depend on it, as on Android.
    val crashLooping = IosStartupCrashes.crashes.isCrashLooping
    return ComposeUIViewController {
        var safeMode by remember { mutableStateOf(crashLooping) }
        if (safeMode) {
            IosSafeMode(
                report = IosStartupCrashes.crashes::report,
                onReset = {
                    IosStartupCrashes.resetLocalData()
                    safeMode = false
                },
                onContinue = { safeMode = false },
            )
        } else {
            val app = remember { startApp() }
            setSingletonImageLoaderFactory { context -> imageLoader(context, app.graph) }
            // A launch that stays up this long got going: it no longer counts towards safe mode.
            LaunchedEffect(Unit) {
                delay(StartupCrashes.STARTUP_WINDOW_MILLIS)
                IosStartupCrashes.crashes.launchSucceeded()
            }
            OrbinApp(
                app.browser,
                app.lock,
                app.downloads,
                app.backup,
                formFactor(),
                AppVersion(app.graph.appVersion, app.graph.appBuild),
                app.graph.whatsNew,
                app.graph.pages,
            )
        }
    }.also { controller -> attachPencil(controller.view) }
}

/** What the app runs on, made once safe mode is out of the way. */
private class StartedApp(
    val graph: AppGraph,
    val browser: Browser,
    val lock: AppLock,
    val downloads: MediaDownloads,
    val backup: IosBackup,
)

/**
 * Builds the app over [AppGraph]: one HTTP client for the providers, image loading and saves, and
 * one database for what the reader keeps, shared with the background refresh.
 */
private fun startApp(): StartedApp {
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
    val lock =
        AppLock(
            graph.settingsStore.settings,
            graph.settingsStore::setBiometricLockEnabled,
            DeviceOwnerAuthenticator(),
            graph.scope,
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
            isAuthorized = { !lock.state.value.locked && !lock.state.value.obscured },
            isOwnMediaUrl = { id, url ->
                graph.providers
                    .firstOrNull { it.metadata.id == id }
                    ?.metadata
                    ?.isOwnMediaUrl(url) == true
            },
        )
    var pendingNotificationJob: Job? = null
    graph.notifier.attachNavigation { key ->
        pendingNotificationJob?.cancel()
        pendingNotificationJob =
            graph.scope.launch {
                lock.state.first { !it.locked && !it.obscured }
                browser.openThread(key)
            }
    }

    // The app lives as long as this controller, so the observers are never removed.
    observe(UIApplicationDidBecomeActiveNotification) {
        lock.onForeground()
        browser.watched.refresh()
        UNUserNotificationCenter.currentNotificationCenter().setBadgeCount(0, withCompletionHandler = null)
    }
    observe(UIApplicationWillResignActiveNotification) {
        lock.onResignActive()
    }
    observe(UIApplicationDidEnterBackgroundNotification) {
        lock.onBackground()
        if (lock.state.value.locked) backup.cancelPending()
        // Leaving the app is when a background refresh is worth asking for.
        scheduleBackgroundRefresh()
    }
    return StartedApp(graph, browser, lock, downloads, backup)
}

/** Coil over the app's Ktor client, with memory-only caching. */
@OptIn(ExperimentalForeignApi::class)
private fun imageLoader(
    context: PlatformContext,
    graph: AppGraph,
): ImageLoader =
    ImageLoader
        .Builder(context)
        .components { add(KtorNetworkFetcherFactory(httpClient = { graph.client })) }
        .diskCache(null)
        .crossfade(false)
        .build()

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

/** Also run for background launches, before any lazy image loader is requested. */
@OptIn(ExperimentalForeignApi::class)
internal fun retireLegacyIosImageCache() {
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
    if (cacheDirectory.isNotBlank()) {
        val legacyCache = "$cacheDirectory/image_cache"
        val manager = NSFileManager.defaultManager
        check(!manager.fileExistsAtPath(legacyCache) || manager.removeItemAtPath(legacyCache, error = null)) {
            "Could not retire legacy image cache"
        }
    }
}
