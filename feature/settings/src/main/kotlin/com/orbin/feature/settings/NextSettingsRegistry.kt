package com.orbin.feature.settings

import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.ColorTheme
import com.orbin.core.model.DohProvider
import com.orbin.core.model.DownloadOrganization
import com.orbin.core.model.FeedRefreshInterval
import com.orbin.core.model.FeedSort
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
 * Every row is editable where it stands. A toggle flips, a choice opens its options underneath, a
 * text field opens underneath, an action runs here — a system picker opens over this screen rather
 * than a screen of ours opening under it — and the two rows that are guarantees rather than choices
 * say so and cannot be pressed. Nothing navigates: a settings list that sends you to another
 * interface to change a setting is two interfaces.
 */
internal fun buildSettings(
    settings: AppSettings,
    vm: SettingsViewModel,
    updateState: String,
    dnsFallbackActive: Boolean,
): SettingsModel {
    val rows = Rows()
    val groups =
        listOf(
            CONTENT to rows.content(settings, vm),
            APPEARANCE to rows.appearance(settings, vm),
            MEDIA to rows.media(settings, vm),
            NOTIFICATIONS to rows.notifications(settings, vm),
            PRIVACY to rows.privacy(settings, vm, updateState, dnsFallbackActive),
            STORAGE to rows.storage(settings, vm),
            ADVANCED to rows.advanced(settings, vm),
        )
    return SettingsModel(groups, rows.toggles.toMap(), rows.choices.toMap(), rows.texts.toMap())
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
    val texts = mutableMapOf<String, (String) -> Unit>()

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

    /**
     * A string setting, edited under its own row.
     *
     * [value] is what the row shows — often a summary such as "3 tags" — while [current] is the raw
     * string the editor is seeded with, so a row can read well closed and still edit truthfully.
     */
    fun text(
        id: String,
        label: String,
        value: String,
        current: String,
        hint: String,
        onChange: (String) -> Unit,
    ): SettingItem {
        texts[id] = onChange
        return SettingItem(id, label, value, SettingKind.TEXT, text = current, hint = hint)
    }

    /** Something that happens here: a picker, an export, a check. Dispatched by id in the screen. */
    fun action(
        id: String,
        label: String,
        value: String,
        hint: String? = null,
    ): SettingItem = SettingItem(id, label, value, SettingKind.ACTION, hint = hint)

    /** A guarantee rather than a choice: stated, and not pressable. */
    fun info(
        id: String,
        label: String,
        value: String,
        hint: String? = null,
    ): SettingItem = SettingItem(id, label, value, SettingKind.INFO, hint = hint)

    fun content(
        settings: AppSettings,
        vm: SettingsViewModel,
    ) = listOf(
        // Stated, never offered: the permanent filter has no setter and never will.
        info("permanentFilter", "Built-in content filter", "Always on"),
        toggle(
            "harshFilter",
            "Filter everyday shock words",
            settings.harshContentFilter,
            vm::setHarshContentFilter,
        ),
        toggle("personalized", "Personalized home feed", settings.personalizedHomeFeed, vm::setPersonalizedHomeFeed),
        text(
            "hiddenTags",
            "Hidden tags",
            settings.hiddenTags.tagSummary(),
            settings.hiddenTags,
            "Comma-separated. Threads matching any of them are hidden.",
            vm::setHiddenTags,
        ),
        text(
            "mutedTags",
            "Muted tags",
            settings.mutedTags.tagSummary(),
            settings.mutedTags,
            "Comma-separated. Threads matching any of them are collapsed rather than removed.",
            vm::setMutedTags,
        ),
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
        choice(
            "feedSort",
            "Sort feed by",
            FeedSort.entries,
            settings.feedSort,
            { it.label },
            vm::setFeedSort,
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
            "threadPresentation",
            "Open threads as",
            ThreadPresentation.entries,
            settings.threadPresentation,
            { it.label },
            vm::setThreadPresentation,
        ),
        // The label, not the id or the stored key: renaming either would reset the preference for
        // everyone who has it on. It covers the catalog and the media wall now as well as the feed.
        toggle(
            "fullScreenFeed",
            "Full-screen browsing",
            settings.fullScreenFeedChrome,
            vm::setFullScreenFeedChrome,
        ),
        choice(
            "fontScale",
            "Font size",
            FontScaleOption.entries,
            FontScaleOption.fromScale(settings.fontScale),
            { it.label },
            { option -> vm.setFontScale(option.scale) },
        ),
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
    ) = listOfNotNull(
        toggle(
            "watchNotifications",
            "Thread watch notifications",
            settings.threadWatchNotificationsEnabled,
            vm::setThreadWatchNotifications,
        ),
        // Quiet hours only mean anything while there are notifications to be quiet about.
        if (settings.threadWatchNotificationsEnabled) {
            text(
                "quietStart",
                "Quiet hours start",
                settings.quietHoursStart.orNotSet(),
                settings.quietHoursStart,
                QUIET_HOURS_HINT,
                vm::setQuietHoursStart,
            )
        } else {
            null
        },
        if (settings.threadWatchNotificationsEnabled) {
            text(
                "quietEnd",
                "Quiet hours end",
                settings.quietHoursEnd.orNotSet(),
                settings.quietHoursEnd,
                QUIET_HOURS_HINT,
                vm::setQuietHoursEnd,
            )
        } else {
            null
        },
    )

    fun privacy(
        settings: AppSettings,
        vm: SettingsViewModel,
        updateState: String,
        dnsFallbackActive: Boolean,
    ) = listOfNotNull(
        // Stated rather than offered, the same as the content filter: there is no plaintext mode.
        info("httpsOnly", "HTTPS only", "Always enforced"),
        toggle("biometric", "Lock with biometrics", settings.biometricLockEnabled, vm::setBiometricLock),
        toggle("recentSearches", "Save recent searches", settings.saveRecentSearches, vm::setSaveRecentSearches),
        choice("doh", "DNS over HTTPS", DohProvider.entries, settings.dohProvider, { it.label }, vm::setDohProvider),
        info(
            "dnsPrivacy",
            "DNS privacy",
            if (dnsFallbackActive) "Not private right now" else "Encrypted",
            if (dnsFallbackActive) {
                "This network is blocking ${settings.dohProvider.label}, so lookups are going through the " +
                    "system resolver. Try another resolver above, or another network."
            } else {
                "Encrypted DNS is always on. If a network blocks the resolver you pick, Orbin falls back to " +
                    "the system resolver and says so here rather than failing to load."
            },
        ),
        // Stored inverted — the setting is "disable OCSP", the row is the guarantee it provides.
        toggle(
            "ocsp",
            "Certificate revocation checks",
            !settings.disableOcspChecking,
            vm::setCertificateRevocationChecks,
        ),
        text(
            "userAgent",
            "Custom user agent",
            settings.userAgent.ifBlank { "Default" },
            settings.userAgent,
            "Sent with every request. Leave empty to use Orbin's default.",
            vm::setUserAgent,
        ),
        choice(
            "connectTimeout",
            "Connect timeout",
            CONNECT_TIMEOUTS_SECONDS,
            settings.connectTimeoutSeconds,
            { "${it}s" },
            vm::setConnectTimeout,
        ),
        choice(
            "readTimeout",
            "Read timeout",
            READ_TIMEOUTS_SECONDS,
            settings.readTimeoutSeconds,
            { "${it}s" },
            vm::setReadTimeout,
        ),
        action(
            "clearActivity",
            "Clear local activity",
            "Delete",
            "Deletes browsing history, recent searches and download history stored on this device.",
        ),
        action(
            "crashDetails",
            "Crash details",
            "Save",
            "Saves a copy of any recorded crashes to a file you choose. Nothing is sent anywhere on its own.",
        ),
        // Only when the in-app updater is on: a check you cannot run is not a setting.
        if (settings.internalUpdaterEnabled) {
            action("checkUpdates", "Check for updates", updateState, "Asks GitHub whether a newer release exists.")
        } else {
            null
        },
    )

    fun storage(
        settings: AppSettings,
        vm: SettingsViewModel,
    ) = listOf(
        choice(
            "cacheLimit",
            "Image cache limit",
            IMAGE_CACHE_LIMITS_MB,
            settings.imageCacheLimitMb,
            { "$it MB" },
            vm::setImageCacheLimitMb,
        ),
        action(
            "downloadFolder",
            "Saved media folder",
            settings.downloadFolderUri.ifBlank { "Downloads/Orbin" },
            "Opens the system folder picker.",
        ),
        choice(
            "downloadOrg",
            "Organize downloads",
            DownloadOrganization.entries,
            settings.downloadOrganization,
            { it.label },
            vm::setDownloadOrganization,
        ),
        action(
            "exportBackup",
            "Export data",
            "Save",
            "Writes settings, boards, bookmarks and saved searches to a file you choose. " +
                "It is plain JSON and is not encrypted.",
        ),
        action(
            "importBackup",
            "Import data",
            "Restore",
            "Merges a backup into what is already here, so a restore cannot destroy an existing setup.",
        ),
    )

    fun advanced(
        settings: AppSettings,
        vm: SettingsViewModel,
    ) = listOf(
        toggle("internalUpdater", "In-app updates", settings.internalUpdaterEnabled, vm::setInternalUpdater),
        action("runSetup", "Run setup again", "Start", "Walks through the first-run setup from the beginning."),
    )
}

/** The four steps the appearance screen offered; font size was never free-form. */
internal enum class FontScaleOption(
    val scale: Float,
    val label: String,
) {
    SMALL(FONT_SCALE_SMALL, "Small"),
    DEFAULT(FONT_SCALE_DEFAULT, "Default"),
    LARGE(FONT_SCALE_LARGE, "Large"),
    XLARGE(FONT_SCALE_EXTRA_LARGE, "XL"),
    ;

    companion object {
        fun fromScale(scale: Float): FontScaleOption =
            entries.minByOrNull { option -> kotlin.math.abs(option.scale - scale) } ?: DEFAULT
    }
}

private const val FONT_SCALE_SMALL = 0.9f
private const val FONT_SCALE_DEFAULT = 1f
private const val FONT_SCALE_LARGE = 1.1f
private const val FONT_SCALE_EXTRA_LARGE = 1.2f

private const val QUIET_HOURS_HINT = "HH:MM, 24-hour. Leave empty to disable."
private val CONNECT_TIMEOUTS_SECONDS = listOf(10L, 15L, 30L, 60L)
private val READ_TIMEOUTS_SECONDS = listOf(15L, 30L, 60L, 120L)
private val IMAGE_CACHE_LIMITS_MB = listOf(128, 256, 512, 1024)

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
    private val texts: Map<String, (String) -> Unit>,
) {
    val count: Int get() = groups.sumOf { it.second.size }

    fun toggle(id: String) = toggles[id]?.invoke()

    fun choose(
        id: String,
        index: Int,
    ) = choices[id]?.invoke(index)

    fun commit(
        id: String,
        value: String,
    ) = texts[id]?.invoke(value.trim())
}

/** SYSTEM -> "System". These enums carry no label, and shouting at the reader is not a design. */
private fun Enum<*>.titleCase(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun String.orNotSet(): String = ifBlank { "Not set" }

private fun String.tagSummary(): String {
    val count = split(',').map { it.trim() }.count { it.isNotEmpty() }
    return if (count == 0) "None" else "$count"
}
