package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.FormFactor
import com.orbin.core.model.ProviderId
import com.orbin.core.model.TABLET_MAX_CATALOG_COLUMNS
import com.orbin.core.model.TABLET_MIN_CATALOG_COLUMNS
import com.orbin.core.model.feedColumnsFor
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.uinext.OFF_LABEL
import com.orbin.uinext.ON_LABEL
import com.orbin.uinext.SettingItem
import com.orbin.uinext.SettingKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The settings iOS has, read and written through the same `SettingsStore` (`:storage`) and keys as
 * Android's: hiding NSFW boards, covering violent media, the theme and true black, and clearing
 * what the app keeps.
 */
class ReaderSettings(
    private val repository: SettingsRepository,
    private val history: HistoryRepository,
    private val scope: CoroutineScope,
) {
    val current: StateFlow<AppSettings> =
        repository.settings.stateIn(
            scope,
            SharingStarted.Eagerly,
            AppSettings.Default,
        )

    fun setHideNsfwBoards(hide: Boolean): Job = scope.launch { repository.setHideNsfwBoards(hide) }

    fun setCoverViolentMedia(cover: Boolean): Job = scope.launch { repository.setCoverViolentMedia(cover) }

    fun setThemeMode(mode: AppThemeMode): Job = scope.launch { repository.setThemeMode(mode) }

    fun setAmoled(amoled: Boolean): Job = scope.launch { repository.setAmoled(amoled) }

    fun setActiveProviderId(site: ProviderId): Job = scope.launch { repository.setActiveProviderId(site) }

    fun setTabletCatalogColumns(columns: Int): Job = scope.launch { repository.setTabletCatalogColumns(columns) }

    fun setFeedColumns(
        formFactor: FormFactor,
        columns: Int,
    ): Job = scope.launch { repository.setFeedColumns(formFactor, columns) }

    /** Deletes the reading history, which is all the local activity iOS keeps so far. */
    fun clearActivity(): Job = scope.launch { history.clear() }
}

/** What the settings rows are called; the screen dispatches on these. */
internal object SettingIds {
    const val HIDE_NSFW = "hideNsfw"
    const val COVER_VIOLENT = "coverViolent"
    const val THEME = "themeMode"
    const val AMOLED = "amoled"
    const val FEED_COLUMNS = "feedColumns"
    const val CATALOG_COLUMNS = "catalogColumns"
    const val APP_LOCK = "biometric"
    const val CLEAR_ACTIVITY = "clearActivity"
    const val CLEAR_IMAGE_CACHE = "clearImageCache"
    const val EXPORT_BACKUP = "exportBackup"
    const val IMPORT_BACKUP = "importBackup"
}

/**
 * The rows, in Android's words and order: its preferences, and its data section less updates,
 * which the App Store handles on iOS. Clearing activity takes two taps, as on Android: the first
 * arms the row ([clearArmed]). The backup rows say how the last export or import went ([backup]).
 */
internal fun settingsGroups(
    settings: AppSettings,
    clearArmed: Boolean,
    imageCacheCleared: Boolean,
    backup: BackupState = BackupState.Idle,
    formFactor: FormFactor = FormFactor.PHONE,
): List<Pair<String, List<SettingItem>>> =
    listOf(
        "" to
            listOfNotNull(
                toggle(SettingIds.HIDE_NSFW, "Hide NSFW boards", settings.hideNsfwBoards),
                toggle(SettingIds.COVER_VIOLENT, "Cover violent media", settings.coverViolentMedia),
                SettingItem(
                    id = SettingIds.THEME,
                    label = "Theme",
                    value = settings.themeMode.label,
                    kind = SettingKind.CHOICE,
                    options = AppThemeMode.entries.map { it.label },
                    selected = settings.themeMode.ordinal,
                ),
                toggle(SettingIds.AMOLED, "True black", settings.amoled),
                feedColumns(settings, formFactor),
                catalogColumns(settings, formFactor),
                toggle(SettingIds.APP_LOCK, "App lock", settings.biometricLockEnabled),
            ),
        "" to
            listOf(
                SettingItem(
                    id = SettingIds.CLEAR_ACTIVITY,
                    label = "Clear local activity",
                    value = if (clearArmed) "Tap again to delete" else "Delete",
                    kind = SettingKind.ACTION,
                    hint = "Deletes browsing history on this device.",
                ),
                SettingItem(
                    id = SettingIds.CLEAR_IMAGE_CACHE,
                    label = "Clear image cache",
                    value = if (imageCacheCleared) "Cleared" else "Clear",
                    kind = SettingKind.ACTION,
                ),
                SettingItem(
                    id = SettingIds.EXPORT_BACKUP,
                    label = "Export data",
                    value =
                        when (backup) {
                            BackupState.Working -> "Working…"
                            BackupState.Exported -> "Saved"
                            else -> "Save"
                        },
                    kind = SettingKind.ACTION,
                    hint = "Followed boards, watched threads and settings, as a file Android can restore too.",
                ),
                SettingItem(
                    id = SettingIds.IMPORT_BACKUP,
                    label = "Import data",
                    value =
                        when (backup) {
                            is BackupState.Imported -> "Restored ${backup.boards} boards, ${backup.bookmarks} threads"
                            is BackupState.Failed -> backup.message
                            else -> "Restore"
                        },
                    kind = SettingKind.ACTION,
                ),
            ),
    )

/** An iPad's feed column count, as Android's tablet row: only where the device has a choice. */
private fun feedColumns(
    settings: AppSettings,
    formFactor: FormFactor,
): SettingItem? {
    if (formFactor.maxFeedColumns <= 1) return null
    val columns = settings.feedColumnsFor(formFactor).coerceIn(1, formFactor.maxFeedColumns)
    return SettingItem(
        id = SettingIds.FEED_COLUMNS,
        label = "Feed columns",
        value = columns.toString(),
        kind = SettingKind.CHOICE,
        options = (1..formFactor.maxFeedColumns).map { it.toString() },
        selected = columns - 1,
    )
}

/** An iPad's board catalog column count, as Android's tablet row. */
private fun catalogColumns(
    settings: AppSettings,
    formFactor: FormFactor,
): SettingItem? {
    if (formFactor != FormFactor.TABLET) return null
    val choices = (TABLET_MIN_CATALOG_COLUMNS..TABLET_MAX_CATALOG_COLUMNS).toList()
    val columns = settings.tabletCatalogColumns.coerceIn(choices.first(), choices.last())
    return SettingItem(
        id = SettingIds.CATALOG_COLUMNS,
        label = "Catalog columns",
        value = columns.toString(),
        kind = SettingKind.CHOICE,
        options = choices.map { it.toString() },
        selected = choices.indexOf(columns),
    )
}

private fun toggle(
    id: String,
    label: String,
    on: Boolean,
) = SettingItem(id, label, if (on) ON_LABEL else OFF_LABEL, SettingKind.TOGGLE)

/** SYSTEM -> "System", as Android labels it. */
private val AppThemeMode.label: String get() = name.lowercase().replaceFirstChar(Char::uppercase)
