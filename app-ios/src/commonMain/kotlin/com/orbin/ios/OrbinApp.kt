package com.orbin.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.FormFactor
import com.orbin.domain.repository.WhatsNewRepository
import com.orbin.uinext.components.LibraryNavigation
import com.orbin.uinext.components.LocalLibraryNavigation
import com.orbin.uinext.components.NextDestination
import com.orbin.uinext.components.NextLoading
import com.orbin.uinext.components.NextPlatform
import com.orbin.uinext.components.NextTheme
import com.orbin.uinext.components.OrbinAdaptiveNavigation
import com.orbin.uinext.components.OrbinSideBar
import com.orbin.uinext.components.canonicalNextPalette
import com.orbin.uinext.components.reader
import com.orbin.uinext.components.readerKeyboardShortcuts
import com.orbin.uinext.screens.LockScreen
import com.orbin.uinext.screens.OnboardingBoard
import com.orbin.uinext.screens.OnboardingBoards
import com.orbin.uinext.screens.OnboardingScreen
import com.orbin.uinext.screens.OnboardingSetupConfig
import com.orbin.uinext.screens.SetupThemeOption
import com.orbin.uinext.screens.UpdateThemeConfig
import com.orbin.uinext.screens.WhatsNewOnUpdate
import kotlinx.coroutines.flow.map

/**
 * The whole iOS app: the shared `ui-next` screens over [browser]. Back is the system edge swipe,
 * which Compose Multiplatform delivers to [NavigationBackHandler] on iOS.
 */
@Composable
fun OrbinApp(
    browser: Browser,
    lock: AppLock,
    downloads: MediaDownloads,
    backup: IosBackup,
    formFactor: FormFactor = FormFactor.PHONE,
    version: AppVersion = AppVersion(),
    whatsNew: WhatsNewRepository? = null,
    pages: ThreadPages? = null,
    onStatusBarAppearance: (lightIcons: Boolean) -> Unit = {},
    cloudSync: CloudSyncController? = null,
) {
    val backStack by browser.backStack.collectAsState()
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = backStack.size > 1,
        onBackCompleted = { browser.back() },
    )

    val settings by browser.settings.current.collectAsState()
    val lockState by lock.state.collectAsState()
    val firstRun by browser.firstRun.state.collectAsState()
    val readerReady = firstRun.initialized && !firstRun.needed
    LaunchedEffect(cloudSync, lockState.locked, lockState.obscured, readerReady) {
        if (readerReady && !lockState.locked && !lockState.obscured) {
            cloudSync?.onForeground()
        } else {
            cloudSync?.onLocked()
        }
    }

    // A home-screen quick action opens its destination, as an Android app shortcut does.
    val quickAction by QuickActions.pending.collectAsState()
    LaunchedEffect(quickAction, lockState.locked, lockState.obscured, readerReady) {
        if (!lockState.locked && !lockState.obscured && readerReady) {
            quickAction?.let { tab ->
                browser.openQuickAction(tab)
                QuickActions.opened()
            }
        }
    }
    val fontScale = rememberSystemFontScale()
    val dark =
        when (settings.themeMode) {
            AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
        }
    val palette = canonicalNextPalette(dark, settings.amoled, settings.accentTheme.name)
    val lightIcons = lightStatusBarIcons(backStack.last(), palette.dark, firstRun.needed, lockState)
    SideEffect { onStatusBarAppearance(lightIcons) }
    // Every link goes through one handler, as on Android; "Open in browser" keeps the system's.
    val systemLinks = LocalUriHandler.current
    val links =
        remember(browser, systemLinks) {
            ReaderUriHandler(
                openLinksInApp = { browser.settings.current.value.openLinksInApp },
                inAppAvailable = supportsInAppBrowser,
                openInApp = browser::openWebPage,
                external = systemLinks,
            )
        }
    CompositionLocalProvider(LocalUriHandler provides links) {
        NextTheme(
            darkTheme = palette.dark,
            amoled = palette.amoled,
            palette = palette,
            fontScale = fontScale,
            platform = NextPlatform.IOS,
            showSavedMediaTab = settings.showSavedMediaTab,
        ) {
            // Each screen's scroll position outlives it being covered or left, as on Android.
            val stateHolder = rememberSaveableStateHolder()
            val routeStates = remember { RouteStates() }
            LaunchedEffect(backStack) { routeStates.visit(backStack).forEach(stateHolder::removeState) }
            val readerContent: @Composable (Boolean) -> Unit = { _ ->
                Box(
                    Modifier.fillMaxSize().readerKeyboardShortcuts(
                        onDestination = { destination ->
                            if (readerReady &&
                                !lockState.locked &&
                                !lockState.obscured
                            ) {
                                browser.openPrimaryDestination(destination)
                            }
                        },
                    ),
                ) {
                    if (!lockState.locked && !firstRun.initialized) {
                        NextLoading(Modifier.fillMaxSize())
                    } else if (!lockState.locked && firstRun.needed) {
                        OnboardingDestination(browser)
                    } else if (!lockState.locked) {
                        val route = backStack.last()
                        val sourceReader = sourceReaderRoute(backStack)
                        val primaryDestination = primaryNavigationDestination(sourceReader?.source ?: route)
                        val downloadBadge = rememberActiveDownloadBadge(downloads)
                        val watchedBadge = rememberWatchedBadge(browser)
                        CompositionLocalProvider(
                            LocalLibraryNavigation provides
                                LibraryNavigation(
                                    onSelect = browser::openLibrary,
                                    onPrimary = browser::openPrimaryDestination,
                                    watchedBadge = watchedBadge,
                                    downloadBadge = downloadBadge,
                                    // Library sits above Feed: the edge swipe returns there, as Android's Back does.
                                    onBack = { browser.back() },
                                ),
                        ) {
                            OrbinAdaptiveNavigation(
                                enabled = primaryDestination != null,
                                navigation = {
                                    OrbinSideBar(
                                        selected = checkNotNull(primaryDestination),
                                        onSelect = browser::openPrimaryDestination,
                                        downloadBadge = downloadBadge,
                                        watchedBadge = watchedBadge,
                                    )
                                },
                            ) {
                                val boardReader = boardReaderRoute(backStack)
                                if (boardReader != null) {
                                    BoardReaderDestination(browser, boardReader, downloads, pages, stateHolder)
                                } else if (sourceReader != null) {
                                    SourceReaderDestination(browser, sourceReader, downloads, pages, stateHolder)
                                } else {
                                    stateHolder.SaveableStateProvider(route.stateKey) {
                                        Destination(
                                            browser,
                                            lock,
                                            downloads,
                                            backup,
                                            formFactor,
                                            version,
                                            route,
                                            pages,
                                            cloudSync,
                                            systemLinks,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    LockCover(lock, lockState)
                }
            }
            // This release's notes, once after an update, never over the lock.
            if (whatsNew != null) {
                WhatsNewOnUpdate(
                    whatsNew.lastSeen,
                    whatsNew::markSeen,
                    visible = !lockState.locked && !lockState.obscured && readerReady,
                    themeConfig =
                        UpdateThemeConfig(
                            theme = settings.toSetupThemeOption(),
                            onThemeChange = { option -> browser.settings.applySetupTheme(option) },
                        ),
                    content = readerContent,
                )
            } else {
                readerContent(false)
            }
        }
    }
}

/** Detail routes, including full-screen media, do not reserve primary navigation space. */
internal fun primaryNavigationDestination(route: Route): NextDestination? =
    when (route) {
        Route.Feed -> NextDestination.FEED
        Route.Boards -> NextDestination.BOARDS
        Route.Downloads -> NextDestination.DOWNLOADS
        Route.Settings -> NextDestination.SETTINGS
        Route.Saved -> NextDestination.SAVED
        Route.Watched -> NextDestination.WATCHED
        Route.Search -> NextDestination.SEARCH
        else -> null
    }

/** First run: Android's board picker, over the active site's boards. */
@Composable
private fun OnboardingDestination(browser: Browser) {
    val setup by browser.firstRun.state.collectAsState()
    val directory by browser.boardDirectory.collectAsState()
    val settings by browser.settings.current.collectAsState()
    val followed by browser.followed.collectAsState()
    val site by browser.activeSite.collectAsState()
    val boards = remember(directory, site) { directory.boardsOnSite(site) }
    val onSite =
        remember(boards, settings.nsfwContentHidden) {
            (boards as? Load.Ready)?.value.orEmpty().filterNot { settings.nsfwContentHidden && it.board.isNsfw }
        }
    val eligible =
        remember(directory, followed, settings.nsfwContentHidden) {
            directory.eligibleFollowing(followed, settings.nsfwContentHidden)
        }
    val byId = remember(onSite) { onSite.associateBy { it.board.id.value } }
    OnboardingScreen(
        boards =
            remember(boards, onSite) {
                when (val loaded = boards) {
                    Load.Loading -> OnboardingBoards.Loading
                    is Load.Failed -> OnboardingBoards.Failed(loaded.message)
                    is Load.Ready ->
                        OnboardingBoards.Ready(
                            onSite.map {
                                OnboardingBoard(
                                    it.board.id.value,
                                    it.board.title,
                                    it.board.description,
                                    it.board.isNsfw,
                                )
                            },
                        )
                }
            },
        following =
            remember(eligible, site) {
                eligible.filter { it.provider == site }.mapTo(mutableSetOf()) { it.board.value }
            },
        sites = remember { browser.sites.map { it.name } },
        selectedSite = browser.sites.indexOfFirst { it.id == site },
        onSelectSite = { index -> browser.selectSite(browser.sites[index].id) },
        onFollow = { id, follow -> byId[id]?.let { browser.setFollowed(it, follow) } },
        onRetry = browser::retry,
        onStart = { browser.completeFirstRun() },
        totalFollowing = eligible.size,
        isSaving = setup.isSaving,
        errorMessage = setup.errorMessage,
        canStart = setup.canStart && eligible.isNotEmpty(),
        setupWizard =
            OnboardingSetupConfig(
                theme = settings.toSetupThemeOption(),
                hideNsfwBoards = settings.hideNsfwBoards,
                coverViolentMedia = settings.coverViolentMedia,
                onThemeChange = { option -> browser.settings.applySetupTheme(option) },
                onHideNsfwChange = browser.settings::setHideNsfwBoards,
                onCoverViolentMediaChange = browser.settings::setCoverViolentMedia,
            ),
    )
}

private fun com.orbin.core.model.AppSettings.toSetupThemeOption(): SetupThemeOption =
    when {
        themeMode == AppThemeMode.SYSTEM -> SetupThemeOption.SYSTEM
        themeMode == AppThemeMode.LIGHT -> SetupThemeOption.LIGHT
        amoled -> SetupThemeOption.AMOLED
        else -> SetupThemeOption.DARK
    }

private fun ReaderSettings.applySetupTheme(option: SetupThemeOption) {
    when (option) {
        SetupThemeOption.SYSTEM -> {
            setThemeMode(AppThemeMode.SYSTEM)
            setAmoled(false)
        }
        SetupThemeOption.LIGHT -> {
            setThemeMode(AppThemeMode.LIGHT)
            setAmoled(false)
        }
        SetupThemeOption.DARK -> {
            setThemeMode(AppThemeMode.DARK)
            setAmoled(false)
        }
        SetupThemeOption.AMOLED -> {
            setThemeMode(AppThemeMode.DARK)
            setAmoled(true)
        }
    }
}

@Composable
private fun Destination(
    browser: Browser,
    lock: AppLock,
    downloads: MediaDownloads,
    backup: IosBackup,
    formFactor: FormFactor,
    version: AppVersion,
    route: Route,
    pages: ThreadPages?,
    cloudSync: CloudSyncController?,
    systemLinks: UriHandler,
) {
    when (route) {
        Route.Feed -> FeedDestination(browser, downloads)
        Route.Boards -> BoardsDestination(browser, downloads)
        Route.Downloads -> DownloadsDestination(browser, downloads)
        Route.Saved, Route.History, Route.Watched -> ThreadCollectionsDestination(browser, route)
        Route.Search -> SearchDestination(browser)
        Route.Settings -> SettingsDestination(browser, lock, backup, formFactor, version, downloads, cloudSync)
        is Route.Catalog -> CatalogDestination(browser, route.board)
        is Route.BoardMedia -> BoardMediaDestination(browser, route.board)
        is Route.ThreadPage -> ThreadDestination(browser, downloads, pages)
        is Route.Media -> MediaDestination(browser, downloads, route)
        is Route.ThreadVideos -> ThreadVideosDestination(browser, downloads, route)
        is Route.WebPage -> WebPageDestination(browser, route, systemLinks)
    }
}

/** The app lock over everything: the lock screen while locked, a blank cover while hidden. */
@Composable
private fun LockCover(
    lock: AppLock,
    state: LockState,
) {
    if (!state.locked && !state.obscured) return
    // Taps stop here rather than reaching the app underneath.
    Box(Modifier.fillMaxSize().background(reader.background).pointerInput(Unit) {}) {
        if (state.locked) LockScreen(message = state.message, unlocking = state.unlocking, onUnlock = { lock.unlock() })
    }
}
