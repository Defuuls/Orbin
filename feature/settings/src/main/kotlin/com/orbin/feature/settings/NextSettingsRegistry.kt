package com.orbin.feature.settings

import com.orbin.core.model.AppColorTheme
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.FormFactor
import com.orbin.core.model.TABLET_MAX_CATALOG_COLUMNS
import com.orbin.core.model.TABLET_MIN_CATALOG_COLUMNS
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
    strings: SettingsStrings,
    imageCacheLabel: String = strings.get(R.string.settings_image_cache_empty),
    clearArmed: Boolean = false,
    checkUpdatesOnLaunch: Boolean = true,
    formFactor: FormFactor = FormFactor.PHONE,
    dns: DnsRowState = DnsRowState(),
): SettingsModel {
    val rows = Rows(strings)
    val groups =
        listOf(
            rows.preferences(settings, vm, checkUpdatesOnLaunch, formFactor) + rows.strictDns(dns, vm),
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
private class Rows(
    private val strings: SettingsStrings,
) {
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
        hint: String? = null,
    ): SettingItem {
        toggles[id] = { onChange(!value) }
        return SettingItem(id, label, if (value) ON_LABEL else OFF_LABEL, SettingKind.TOGGLE, hint = hint)
    }

    /**
     * Strict encrypted DNS, with a hint that turns into a warning while lookups are going through
     * the network's resolver — the only place the app says that its DNS stopped being private.
     */
    fun strictDns(
        dns: DnsRowState,
        vm: SettingsViewModel,
    ): SettingItem =
        toggle(
            "strictDns",
            strings.get(R.string.settings_strict_dns),
            dns.strict,
            vm::setStrictDns,
            hint =
                strings.get(
                    when {
                        dns.strict -> R.string.settings_strict_dns_hint_on
                        dns.fallbackActive -> R.string.settings_strict_dns_hint_fallback
                        else -> R.string.settings_strict_dns_hint_off
                    },
                ),
        )

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
        destructive: Boolean = false,
    ): SettingItem = SettingItem(id, label, value, SettingKind.ACTION, hint = hint, destructive = destructive)

    fun preferences(
        settings: AppSettings,
        vm: SettingsViewModel,
        checkUpdatesOnLaunch: Boolean,
        formFactor: FormFactor,
    ) = listOfNotNull(
        toggle("hideNsfw", strings.get(R.string.settings_hide_nsfw), settings.hideNsfwBoards, vm::setHideNsfwBoards),
        toggle(
            "coverViolent",
            strings.get(R.string.settings_cover_violent),
            settings.coverViolentMedia,
            vm::setCoverViolentMedia,
        ),
        choice(
            "themeMode",
            strings.get(R.string.settings_theme),
            AppThemeMode.entries,
            settings.themeMode,
            Enum<*>::titleCase,
            vm::setThemeMode,
        ),
        choice(
            "colorTheme",
            strings.get(R.string.settings_color_theme),
            AppColorTheme.entries,
            settings.colorTheme,
            AppColorTheme::label,
            vm::setColorTheme,
        ),
        feedColumns(settings, vm, formFactor),
        catalogColumns(settings, vm, formFactor),
        toggle("amoled", strings.get(R.string.settings_true_black), settings.amoled, vm::setAmoled),
        toggle(
            "biometric",
            strings.get(R.string.settings_app_lock),
            settings.biometricLockEnabled,
            vm::setBiometricLock,
        ),
        toggle(
            "updateOnLaunch",
            strings.get(R.string.settings_update_on_launch),
            checkUpdatesOnLaunch,
            vm::setCheckUpdatesOnLaunch,
        ),
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
        val label =
            strings.get(
                if (formFactor ==
                    FormFactor.FOLDABLE
                ) {
                    R.string.settings_feed_columns_unfolded
                } else {
                    R.string.settings_feed_columns
                },
            )
        return choice(
            id = "feedColumns",
            label = label,
            values = (1..formFactor.maxFeedColumns).toList(),
            selected = settings.feedColumnsFor(formFactor),
            text = {
                if (it ==
                    1
                ) {
                    strings.get(R.string.settings_one_column)
                } else {
                    strings.get(R.string.settings_columns, it)
                }
            },
            onChange = { vm.setFeedColumns(formFactor, it) },
        )
    }

    /** A board catalog's columns: a tablet's choice; everywhere else the catalog fits its tiles. */
    private fun catalogColumns(
        settings: AppSettings,
        vm: SettingsViewModel,
        formFactor: FormFactor,
    ): SettingItem? {
        if (formFactor != FormFactor.TABLET) return null
        return choice(
            id = "catalogColumns",
            label = strings.get(R.string.settings_catalog_columns),
            values = (TABLET_MIN_CATALOG_COLUMNS..TABLET_MAX_CATALOG_COLUMNS).toList(),
            selected = settings.tabletCatalogColumns.coerceIn(TABLET_MIN_CATALOG_COLUMNS, TABLET_MAX_CATALOG_COLUMNS),
            text = { strings.get(R.string.settings_columns, it) },
            onChange = vm::setTabletCatalogColumns,
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
            strings.get(R.string.settings_clear_activity),
            // Confirmed in place: the first tap arms the row, a second tap deletes.
            strings.get(
                if (clearArmed) R.string.settings_clear_activity_armed else R.string.settings_clear_activity_action,
            ),
            strings.get(R.string.settings_clear_activity_hint),
            destructive = true,
        ),
        action(
            "clearImageCache",
            strings.get(R.string.settings_clear_image_cache),
            imageCacheLabel,
            destructive = true,
        ),
        action("checkUpdates", strings.get(R.string.settings_check_updates), updateState),
        action("exportBackup", strings.get(R.string.settings_export), strings.get(R.string.settings_export_action)),
        action("importBackup", strings.get(R.string.settings_import), strings.get(R.string.settings_import_action)),
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

/** The strict encrypted DNS row's inputs: the reader's choice, and whether a fallback is happening. */
internal data class DnsRowState(
    val strict: Boolean = false,
    val fallbackActive: Boolean = false,
)

/** The cards carry no headings: a few rows need no sections. */
internal const val NO_HEADING = ""
