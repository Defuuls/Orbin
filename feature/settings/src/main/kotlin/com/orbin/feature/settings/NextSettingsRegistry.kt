package com.orbin.feature.settings

import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.FeedSort
import com.orbin.core.model.ThreadPresentation
import com.orbin.uinext.OFF_LABEL
import com.orbin.uinext.ON_LABEL
import com.orbin.uinext.SettingItem
import com.orbin.uinext.SettingKind

/**
 * The intentionally small public settings surface.
 *
 * Orbin keeps sensible defaults for implementation details and exposes only choices that most
 * people can understand and are likely to change. Existing stored preferences remain compatible;
 * removing a row here does not delete or reset its value.
 */
internal fun buildSettings(
    settings: AppSettings,
    vm: SettingsViewModel,
    updateState: String,
    dnsFallbackActive: Boolean,
): SettingsModel {
    val rows = Rows()
    val groups = listOf(
        "General" to rows.general(settings, vm),
        "Appearance" to rows.appearance(settings, vm),
        "Media" to rows.media(settings, vm),
        "Privacy" to rows.privacy(settings, vm),
        "Data" to rows.data(settings, vm, updateState),
    )
    return SettingsModel(groups, rows.toggles.toMap(), rows.choices.toMap(), rows.texts.toMap())
}

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

    fun action(
        id: String,
        label: String,
        value: String,
        hint: String? = null,
    ): SettingItem = SettingItem(id, label, value, SettingKind.ACTION, hint = hint)

    fun general(settings: AppSettings, vm: SettingsViewModel) = listOf(
        toggle("personalized", "Personalized feed", settings.personalizedHomeFeed, vm::setPersonalizedHomeFeed),
        toggle("hideNsfw", "Hide NSFW boards", settings.hideNsfwBoards, vm::setHideNsfwBoards),
        choice("feedSort", "Feed sort", FeedSort.entries, settings.feedSort, { it.label }, vm::setFeedSort),
    )

    fun appearance(settings: AppSettings, vm: SettingsViewModel) = listOf(
        choice("themeMode", "Theme", AppThemeMode.entries, settings.themeMode, Enum<*>::titleCase, vm::setThemeMode),
        toggle("amoled", "True black", settings.amoled, vm::setAmoled),
        choice(
            "threadPresentation",
            "Open threads",
            ThreadPresentation.entries,
            settings.threadPresentation,
            { it.label },
            vm::setThreadPresentation,
        ),
        toggle("fullScreenFeed", "Full-screen browsing", settings.fullScreenFeedChrome, vm::setFullScreenFeedChrome),
        choice(
            "fontScale",
            "Text size",
            FontScaleOption.entries,
            FontScaleOption.fromScale(settings.fontScale),
            { it.label },
            { vm.setFontScale(it.scale) },
        ),
    )

    fun media(settings: AppSettings, vm: SettingsViewModel) = listOf(
        toggle("autoplay", "Autoplay videos", settings.autoplayVideos, vm::setAutoplay),
        toggle("mute", "Mute by default", settings.muteByDefault, vm::setMute),
        toggle("preload", "Preload images", settings.preloadImages, vm::setPreload),
    )

    fun privacy(settings: AppSettings, vm: SettingsViewModel) = listOf(
        toggle("biometric", "App lock", settings.biometricLockEnabled, vm::setBiometricLock),
        toggle("recentSearches", "Save recent searches", settings.saveRecentSearches, vm::setSaveRecentSearches),
        action(
            "clearActivity",
            "Clear local activity",
            "Delete",
            "Deletes browsing history, recent searches and download history on this device.",
        ),
    )

    fun data(settings: AppSettings, vm: SettingsViewModel, updateState: String) = listOfNotNull(
        action(
            "downloadFolder",
            "Downloads folder",
            settings.downloadFolderUri.ifBlank { "Downloads/Orbin" },
            "Opens the system folder picker.",
        ),
        action("exportBackup", "Export data", "Save"),
        action("importBackup", "Import data", "Restore"),
        toggle("internalUpdater", "In-app updates", settings.internalUpdaterEnabled, vm::setInternalUpdater),
        if (settings.internalUpdaterEnabled) action("checkUpdates", "Check for updates", updateState) else null,
    )
}

internal enum class FontScaleOption(
    val scale: Float,
    val label: String,
) {
    SMALL(0.9f, "Small"),
    DEFAULT(1f, "Default"),
    LARGE(1.1f, "Large"),
    XLARGE(1.2f, "XL"),
    ;

    companion object {
        fun fromScale(scale: Float): FontScaleOption =
            entries.minByOrNull { kotlin.math.abs(it.scale - scale) } ?: DEFAULT
    }
}

internal class SettingsModel(
    val groups: List<Pair<String, List<SettingItem>>>,
    private val toggles: Map<String, () -> Unit>,
    private val choices: Map<String, (Int) -> Unit>,
    private val texts: Map<String, (String) -> Unit>,
) {
    val count: Int get() = groups.sumOf { it.second.size }

    fun toggle(id: String) = toggles[id]?.invoke()

    fun choose(id: String, index: Int) = choices[id]?.invoke(index)

    fun commit(id: String, value: String) = texts[id]?.invoke(value.trim())
}

private fun Enum<*>.titleCase(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
