package com.orbin.feature.settings

import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.FormFactor
import com.orbin.core.model.feedColumnsFor
import com.orbin.uinext.OFF_LABEL
import com.orbin.uinext.ON_LABEL
import com.orbin.uinext.SettingItem
import com.orbin.uinext.SettingKind

/**
 * The whole settings surface: a handful of rows in two untitled cards.
 *
 * Preferences first (what Orbin shows and whether it locks), then the things you do to your data.
 * Places live where they belong: Downloads on the Media tab, Search on the Boards tab. Built from [AppSettings] rather than from category
 * screens, so a row cannot show a stale value or go missing because a screen forgot it. Every row
 * is editable or runnable where it stands.
 *
 * Simple is the rule: anything not listed here is decided by the app, not the reader.
 */
internal fun buildSettings(
    settings: AppSettings,
    vm: SettingsViewModel,
    updateState: String,
    imageCacheLabel: String = "Empty · Clear",
    clearArmed: Boolean = false,
    checkUpdatesOnLaunch: Boolean = true,
    formFactor: FormFactor = FormFactor.PHONE,
): SettingsModel {
    val rows = Rows()
    val groups =
        listOf(
            rows.preferences(settings, vm, checkUpdatesOnLaunch, formFactor),
            rows.data(updateState, imageCacheLabel, clearArmed),
        ).map { NO_HEADING to it }
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

    fun preferences(
        settings: AppSettings,
        vm: SettingsViewModel,
        checkUpdatesOnLaunch: Boolean,
        formFactor: FormFactor,
    ) = listOfNotNull(
        toggle("hideNsfw", "Hide NSFW boards", settings.hideNsfwBoards, vm::setHideNsfwBoards),
        choice("themeMode", "Theme", AppThemeMode.entries, settings.themeMode, Enum<*>::titleCase, vm::setThemeMode),
        feedColumns(settings, vm, formFactor),
        toggle("amoled", "True black", settings.amoled, vm::setAmoled),
        toggle("biometric", "App lock", settings.biometricLockEnabled, vm::setBiometricLock),
        toggle("updateOnLaunch", "Tell me about new releases", checkUpdatesOnLaunch, vm::setCheckUpdatesOnLaunch),
    )

    /**
     * Only a device with a wide screen has a choice: a foldable for its inner screen, a tablet for
     * its one. A phone, and a foldable's front screen, always show one column.
     */
    private fun feedColumns(
        settings: AppSettings,
        vm: SettingsViewModel,
        formFactor: FormFactor,
    ): SettingItem? {
        if (formFactor.maxFeedColumns <= 1) return null
        val label = if (formFactor == FormFactor.FOLDABLE) "Feed columns when unfolded" else "Feed columns"
        return choice(
            id = "feedColumns",
            label = label,
            values = (1..formFactor.maxFeedColumns).toList(),
            selected = settings.feedColumnsFor(formFactor),
            text = { if (it == 1) "1 column" else "$it columns" },
            onChange = { vm.setFeedColumns(formFactor, it) },
        )
    }

    /** Only destructive actions carry a hint, and it says what goes. */
    fun data(
        updateState: String,
        imageCacheLabel: String,
        clearArmed: Boolean,
    ) = listOf(
        action(
            "clearActivity",
            "Clear local activity",
            // Confirmed in place: the first tap arms the row, a second tap deletes.
            if (clearArmed) "Tap again to delete" else "Delete",
            "Deletes browsing history, recent searches and download history on this device.",
        ),
        action("clearImageCache", "Clear image cache", imageCacheLabel),
        action("checkUpdates", "Check for updates", updateState),
        action("exportBackup", "Export data", "Save"),
        action("importBackup", "Import data", "Restore"),
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

/** The cards carry no headings: a few rows need no sections. */
internal const val NO_HEADING = ""
