package com.orbin.feature.settings

import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.ColorTheme
import com.orbin.core.model.FeedSort
import com.orbin.core.model.ThreadPresentation
import com.orbin.uinext.OFF_LABEL
import com.orbin.uinext.ON_LABEL
import com.orbin.uinext.SettingItem
import com.orbin.uinext.SettingKind

/**
 * The intentionally small settings surface, as one list under three headings.
 *
 * Built from [AppSettings] rather than from category screens, so a row cannot show a stale value or
 * go missing because a screen forgot it. Every row is editable where it stands: a toggle flips, a
 * choice opens its options underneath, an action runs here. Nothing navigates — a settings list that
 * sends you elsewhere to change a setting is two interfaces.
 *
 * Three headings are enough: how Orbin behaves, how it looks and plays media, and the on-device
 * controls people need occasionally. Rows this list no longer offers keep their stored values
 * untouched; dropping a row here never deletes or resets the preference behind it.
 */
internal fun buildSettings(
    settings: AppSettings,
    vm: SettingsViewModel,
    updateState: String,
): SettingsModel {
    val rows = Rows()
    val groups =
        listOf(
            GENERAL to rows.general(settings, vm),
            DISPLAY to rows.displayAndMedia(settings, vm),
            PRIVACY to rows.privacyAndData(settings, vm, updateState),
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

    /**
     * Empty while no row is a text field.
     *
     * Kept rather than removed because `:ui-next` still renders [SettingKind.TEXT] and the screen
     * still routes commits through [SettingsModel.commit], so a text row can be added back here
     * alone, without re-plumbing either.
     */
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

    /** Something that happens here: a picker, an export, a check. Dispatched by id in the screen. */
    fun action(
        id: String,
        label: String,
        value: String,
        hint: String? = null,
    ): SettingItem = SettingItem(id, label, value, SettingKind.ACTION, hint = hint)

    fun general(
        settings: AppSettings,
        vm: SettingsViewModel,
    ) = listOf(
        toggle("personalized", "Personalized feed", settings.personalizedHomeFeed, vm::setPersonalizedHomeFeed),
        toggle("hideNsfw", "Hide NSFW boards", settings.hideNsfwBoards, vm::setHideNsfwBoards),
        choice("feedSort", "Feed sort", FeedSort.entries, settings.feedSort, { it.label }, vm::setFeedSort),
        choice(
            "threadPresentation",
            "Open threads",
            ThreadPresentation.entries,
            settings.threadPresentation,
            { it.label },
            vm::setThreadPresentation,
        ),
        // The label, not the id or the stored key: renaming either would reset the preference for
        // everyone who has it on. It covers the catalog and the media wall as well as the feed.
        toggle("fullScreenFeed", "Full-screen browsing", settings.fullScreenFeedChrome, vm::setFullScreenFeedChrome),
    )

    fun displayAndMedia(
        settings: AppSettings,
        vm: SettingsViewModel,
    ) = listOf(
        choice("themeMode", "Theme", AppThemeMode.entries, settings.themeMode, Enum<*>::titleCase, vm::setThemeMode),
        choice("colorTheme", "Color scheme", ColorTheme.entries, settings.colorTheme, { it.label }, vm::setColorTheme),
        toggle("amoled", "True black", settings.amoled, vm::setAmoled),
        choice(
            "fontScale",
            "Text size",
            FontScaleOption.entries,
            FontScaleOption.fromScale(settings.fontScale),
            { it.label },
            { option -> vm.setFontScale(option.scale) },
        ),
        toggle("autoplay", "Autoplay videos", settings.autoplayVideos, vm::setAutoplay),
        toggle("mute", "Mute by default", settings.muteByDefault, vm::setMute),
        toggle("preload", "Preload images", settings.preloadImages, vm::setPreload),
    )

    fun privacyAndData(
        settings: AppSettings,
        vm: SettingsViewModel,
        updateState: String,
    ) = listOfNotNull(
        toggle("biometric", "App lock", settings.biometricLockEnabled, vm::setBiometricLock),
        toggle("recentSearches", "Save recent searches", settings.saveRecentSearches, vm::setSaveRecentSearches),
        action(
            "clearActivity",
            "Clear local activity",
            "Delete",
            "Deletes browsing history, recent searches and download history stored on this device.",
        ),
        action(
            "downloadFolder",
            "Downloads folder",
            settings.downloadFolderUri.ifBlank { "Downloads/Orbin" },
            "Opens the system folder picker.",
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
        toggle("internalUpdater", "In-app updates", settings.internalUpdaterEnabled, vm::setInternalUpdater),
        // Only when the in-app updater is on: a check you cannot run is not a setting.
        if (settings.internalUpdaterEnabled) {
            action("checkUpdates", "Check for updates", updateState, "Asks GitHub whether a newer release exists.")
        } else {
            null
        },
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
