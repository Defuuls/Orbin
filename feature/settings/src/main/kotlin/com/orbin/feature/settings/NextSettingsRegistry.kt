package com.orbin.feature.settings

import com.orbin.core.model.AppIconVariant
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.ColorTheme
import com.orbin.core.model.DohProvider
import com.orbin.core.model.DownloadOrganization
import com.orbin.core.model.FeedRefreshInterval
import com.orbin.core.model.FeedThreadLimit
import com.orbin.core.model.MediaFilter
import com.orbin.core.model.PreloadOption
import com.orbin.core.model.PreloadThrottleMode
import com.orbin.core.model.ThreadPresentation
import com.orbin.core.model.ThumbnailSize
import com.orbin.uinext.OFF_LABEL
import com.orbin.uinext.ON_LABEL
import com.orbin.uinext.SettingItem
import com.orbin.uinext.SettingKind

/**
 * Every setting, as one list.
 *
 * Built from [AppSettings] rather than from the seven category screens, so a setting cannot appear
 * here with a stale value or go missing because a screen forgot it. The section names are the ones
 * those screens used, because that is what people have learned — they are waypoints in one list
 * now rather than seven destinations.
 *
 * Three kinds of row. A toggle flips where it stands. A choice opens its options under itself. A
 * link goes somewhere, and is kept for the handful that need a keyboard, a time picker or a file
 * picker: a text field inline in a list this long would be worse than the trip.
 */
internal fun buildSettings(
    settings: AppSettings,
    vm: SettingsViewModel,
): SettingsModel {
    val rows = Rows()
    val groups =
        listOf(
            "Content & feed" to rows.content(settings, vm),
            "Appearance" to rows.appearance(settings, vm),
            "Media & playback" to rows.media(settings, vm),
            "Notifications" to rows.notifications(settings, vm),
            "Privacy & network" to rows.privacy(settings, vm),
            "Storage & backup" to rows.storage(settings, vm),
            "Advanced" to rows.advanced(settings, vm),
        )
    return SettingsModel(groups, rows.toggles.toMap(), rows.choices.toMap())
}

/**
 * Collects the rows and, as it goes, what each one does.
 *
 * A row's effect is recorded from the same value the row displays, so the two cannot drift: a
 * toggle's closure flips the state it was built from rather than re-reading it later.
 */
private class Rows {
    val toggles = mutableMapOf<String, () -> Unit>()
    val choices = mutableMapOf<String, (Int) -> Unit>()

    fun toggle(
        id: String,
        label: String,
        value: Boolean,
        onChange: (Boolean) -> Unit,
    ): SettingItem {
        toggles[id] = { onChange(!value) }
        return SettingItem(id, label, if (value) ON_LABEL else OFF_LABEL, SettingKind.TOGGLE)
    }

    fun <T> choice(
        id: String,
        label: String,
        values: List<T>,
        selected: T,
        text: (T) -> String,
        onChange: (T) -> Unit,
    ): SettingItem {
        choices[id] = { index -> values.getOrNull(index)?.let(onChange) }
        return SettingItem(
            id = id,
            label = label,
            value = text(selected),
            kind = SettingKind.CHOICE,
            options = values.map(text),
            selected = values.indexOf(selected),
        )
    }

    fun content(
        settings: AppSettings,
        vm: SettingsViewModel,
    ) = listOf(
        // Stated, never offered: the permanent filter has no setter and never will.
        SettingItem("permanentFilter", "Built-in content filter", "Always on", SettingKind.LINK),
        toggle("personalized", "Personalized home feed", settings.personalizedHomeFeed, vm::setPersonalizedHomeFeed),
        SettingItem("hiddenTags", "Hidden tags", settings.hiddenTags.tagSummary(), SettingKind.LINK),
        SettingItem("mutedTags", "Muted tags", settings.mutedTags.tagSummary(), SettingKind.LINK),
        toggle("hideNsfw", "Hide NSFW boards", settings.hideNsfwBoards, vm::setHideNsfwBoards),
        toggle("hideTextOnly", "Hide text-only threads", settings.hideTextOnlyThreads, vm::setHideTextOnlyThreads),
        toggle("deepScan", "Deep scan for reply media", settings.deepMediaScan, vm::setDeepMediaScan),
        choice(
            "mediaFilter",
            "Show only",
            MediaFilter.entries,
            settings.mediaFilter,
            Enum<*>::titleCase,
            vm::setMediaFilter,
        ),
        choice(
            "refreshInterval",
            "Refresh feed on return",
            FeedRefreshInterval.entries,
            settings.feedRefreshInterval,
            { it.label },
            vm::setFeedRefreshInterval,
        ),
        choice(
            "threadLimit",
            "Threads per board",
            FeedThreadLimit.entries,
            settings.feedThreadLimit,
            { it.label },
            vm::setFeedThreadLimit,
        ),
    )

    fun appearance(
        settings: AppSettings,
        vm: SettingsViewModel,
    ) = listOf(
        choice("colorTheme", "Color theme", ColorTheme.entries, settings.colorTheme, { it.label }, vm::setColorTheme),
        choice("themeMode", "Theme", AppThemeMode.entries, settings.themeMode, Enum<*>::titleCase, vm::setThemeMode),
        toggle("dynamicColor", "Dynamic color", settings.dynamicColor, vm::setDynamicColor),
        toggle("amoled", "AMOLED black", settings.amoled, vm::setAmoled),
        choice(
            "appIcon",
            "App icon",
            AppIconVariant.entries,
            settings.appIconVariant,
            { it.label },
            vm::setAppIconVariant,
        ),
        choice(
            "threadPresentation",
            "Open threads as",
            ThreadPresentation.entries,
            settings.threadPresentation,
            { it.label },
            vm::setThreadPresentation,
        ),
        toggle("fullScreenFeed", "Full-screen feed", settings.fullScreenFeedChrome, vm::setFullScreenFeedChrome),
        SettingItem("fontScale", "Font size", "${(settings.fontScale * PERCENT).toInt()}%", SettingKind.LINK),
        choice(
            "thumbnailSize",
            "Thumbnail size",
            ThumbnailSize.entries,
            settings.thumbnailSize,
            { it.label },
            vm::setThumbnailSize,
        ),
    )

    fun media(
        settings: AppSettings,
        vm: SettingsViewModel,
    ) = listOf(
        toggle("autoplay", "Autoplay videos", settings.autoplayVideos, vm::setAutoplay),
        toggle("autoplayFeed", "Autoplay videos in feed", settings.autoplayVideosInFeed, vm::setAutoplayVideosInFeed),
        toggle("mute", "Mute by default", settings.muteByDefault, vm::setMute),
        toggle("fullscreenVideo", "Fullscreen video", settings.fullscreenVideoPlayback, vm::setFullscreenVideoPlayback),
        toggle("autoRotate", "Auto-rotate video", settings.autoRotateVideoFullscreen, vm::setAutoRotateVideoFullscreen),
        toggle(
            "mediaScrollThread",
            "Media scroll in thread",
            settings.mediaScrollThreadView,
            vm::setMediaScrollThreadView,
        ),
        toggle("mediaScrollBoard", "Media scroll in board", settings.mediaScrollBoardView, vm::setMediaScrollBoardView),
        toggle("preload", "Preload images", settings.preloadImages, vm::setPreload),
        choice("preloadOption", "Preload content", PreloadOption.entries, settings.preloadOption, {
            it.label
        }, vm::setPreloadOption),
        choice(
            "preloadThrottle",
            "Preload speed",
            PreloadThrottleMode.entries,
            settings.preloadThrottleMode,
            { it.label },
            vm::setPreloadThrottleMode,
        ),
    )

    fun notifications(
        settings: AppSettings,
        vm: SettingsViewModel,
    ) = listOf(
        toggle(
            "watchNotifications",
            "Thread watch notifications",
            settings.threadWatchNotificationsEnabled,
            vm::setThreadWatchNotifications,
        ),
        SettingItem("quietStart", "Quiet hours start", settings.quietHoursStart.orNotSet(), SettingKind.LINK),
        SettingItem("quietEnd", "Quiet hours end", settings.quietHoursEnd.orNotSet(), SettingKind.LINK),
    )

    fun privacy(
        settings: AppSettings,
        vm: SettingsViewModel,
    ) = listOf(
        toggle("biometric", "Lock with biometrics", settings.biometricLockEnabled, vm::setBiometricLock),
        toggle("recentSearches", "Save recent searches", settings.saveRecentSearches, vm::setSaveRecentSearches),
        choice("doh", "DNS over HTTPS", DohProvider.entries, settings.dohProvider, { it.label }, vm::setDohProvider),
        // Stored inverted — the setting is "disable OCSP", the row is the guarantee it provides.
        toggle(
            "ocsp",
            "Certificate revocation checks",
            !settings.disableOcspChecking,
            vm::setCertificateRevocationChecks,
        ),
        SettingItem("userAgent", "Custom user agent", settings.userAgent.ifBlank { "Default" }, SettingKind.LINK),
        SettingItem("connectTimeout", "Connect timeout", "${settings.connectTimeoutSeconds}s", SettingKind.LINK),
        SettingItem("readTimeout", "Read timeout", "${settings.readTimeoutSeconds}s", SettingKind.LINK),
    )

    fun storage(
        settings: AppSettings,
        vm: SettingsViewModel,
    ) = listOf(
        SettingItem("cacheLimit", "Image cache limit", "${settings.imageCacheLimitMb} MB", SettingKind.LINK),
        SettingItem(
            "downloadFolder",
            "Download folder",
            settings.downloadFolderUri.ifBlank { "Default" },
            SettingKind.LINK,
        ),
        choice(
            "downloadOrg",
            "Organize downloads",
            DownloadOrganization.entries,
            settings.downloadOrganization,
            { it.label },
            vm::setDownloadOrganization,
        ),
    )

    fun advanced(
        settings: AppSettings,
        vm: SettingsViewModel,
    ) = listOf(
        toggle("internalUpdater", "In-app updates", settings.internalUpdaterEnabled, vm::setInternalUpdater),
    )
}

/**
 * The rows, plus what each one does.
 *
 * `:ui-next` sees only the rows; the effects stay on this side, keyed by the same id the row
 * carries.
 */
internal class SettingsModel(
    val groups: List<Pair<String, List<SettingItem>>>,
    private val toggles: Map<String, () -> Unit>,
    private val choices: Map<String, (Int) -> Unit>,
) {
    val count: Int get() = groups.sumOf { it.second.size }

    fun toggle(id: String) = toggles[id]?.invoke()

    fun choose(
        id: String,
        index: Int,
    ) = choices[id]?.invoke(index)
}

private const val PERCENT = 100

/** SYSTEM -> "System". These enums carry no label, and shouting at the reader is not a design. */
private fun Enum<*>.titleCase(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun String.orNotSet(): String = ifBlank { "Not set" }

private fun String.tagSummary(): String {
    val count = split(',').map { it.trim() }.count { it.isNotEmpty() }
    return if (count == 0) "None" else "$count"
}
