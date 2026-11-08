package com.orbin.ios

import com.orbin.core.model.AppColorTheme
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.DohProvider
import com.orbin.core.model.FormFactor
import com.orbin.core.model.ProviderId
import com.orbin.core.model.TABLET_MAX_CATALOG_COLUMNS
import com.orbin.core.model.TABLET_MIN_CATALOG_COLUMNS
import com.orbin.core.model.feedColumnsFor
import com.orbin.data.database.dao.DownloadDao
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
    private val downloadDao: DownloadDao? = null,
) {
    val current: StateFlow<AppSettings> =
        repository.settings.stateIn(
            scope,
            SharingStarted.Eagerly,
            AppSettings.Default,
        )

    fun setHideNsfwBoards(hide: Boolean): Job = scope.launch { repository.setHideNsfwBoards(hide) }

    fun setCoverViolentMedia(cover: Boolean): Job = scope.launch { repository.setCoverViolentMedia(cover) }

    fun setColorTheme(theme: AppColorTheme): Job = scope.launch { repository.setColorTheme(theme) }

    fun setThemeMode(mode: AppThemeMode): Job = scope.launch { repository.setThemeMode(mode) }

    fun setAmoled(amoled: Boolean): Job = scope.launch { repository.setAmoled(amoled) }

    fun setActiveProviderId(site: ProviderId): Job = scope.launch { repository.setActiveProviderId(site) }

    fun setTabletCatalogColumns(columns: Int): Job = scope.launch { repository.setTabletCatalogColumns(columns) }

    fun setFeedColumns(
        formFactor: FormFactor,
        columns: Int,
    ): Job = scope.launch { repository.setFeedColumns(formFactor, columns) }

    fun setScreenPrivacy(enabled: Boolean): Job = scope.launch { repository.setScreenPrivacyEnabled(enabled) }

    fun setDohProvider(provider: DohProvider): Job = scope.launch { repository.setDohProvider(provider) }

    fun setStrictDns(enabled: Boolean): Job = scope.launch { repository.setStrictDohEnabled(enabled) }

    fun setSaveSearchHistory(enabled: Boolean): Job = scope.launch { repository.setSaveSearchHistory(enabled) }

    fun setSaveBrowsingHistory(enabled: Boolean): Job = scope.launch { repository.setSaveBrowsingHistory(enabled) }

    /** Deletes the reading history, download records, and invokes cache clearance. */
    fun clearActivity(onClearCaches: (suspend () -> Unit)? = null): Job =
        scope.launch {
            history.clear()
            downloadDao?.clear()
            onClearCaches?.invoke()
        }
}

/** What the settings rows are called; the screen dispatches on these. */
internal object SettingIds {
    const val HIDE_NSFW = "hideNsfw"
    const val PRIVACY = "privacy"
    const val CONTACT = "contact"
    const val COVER_VIOLENT = "coverViolent"
    const val COLOR_THEME = "colorTheme"
    const val THEME = "themeMode"
    const val AMOLED = "amoled"
    const val FEED_COLUMNS = "feedColumns"
    const val CATALOG_COLUMNS = "catalogColumns"
    const val APP_LOCK = "biometric"
    const val SCREEN_PRIVACY = "screenPrivacy"
    const val DOH_PROVIDER = "dohProvider"
    const val STRICT_DOH = "strictDns"
    const val SAVE_SEARCH_HISTORY = "saveSearchHistory"
    const val SAVE_BROWSING_HISTORY = "saveBrowsingHistory"
    const val CLEAR_ACTIVITY = "clearActivity"
    const val CLEAR_IMAGE_CACHE = "clearImageCache"
    const val EXPORT_BACKUP = "exportBackup"
    const val IMPORT_BACKUP = "importBackup"
    const val VERSION = "version"
}

/**
 * Which release this is: the Android release it was cut from ([name], "158-Yuzu", or the bare
 * number from a build made in Xcode) and TestFlight's build number ([build]).
 */
data class AppVersion(
    val name: String = "",
    val build: String = "",
)

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
    version: AppVersion = AppVersion(),
): List<Pair<String, List<SettingItem>>> =
    listOf(
        "" to appearanceAndPrivacyRows(settings, formFactor),
        "" to storageAndAboutRows(clearArmed, imageCacheCleared, backup, version),
    ).let(::settingsSections)

private fun appearanceAndPrivacyRows(
    settings: AppSettings,
    formFactor: FormFactor,
): List<SettingItem> =
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
        colorThemeChoice(settings.colorTheme),
        toggle(SettingIds.AMOLED, "True black", settings.amoled),
        feedColumns(settings, formFactor),
        catalogColumns(settings, formFactor),
        toggle(SettingIds.APP_LOCK, "App lock", settings.biometricLockEnabled),
        toggle(
            SettingIds.SCREEN_PRIVACY,
            "Screen privacy shield",
            settings.screenPrivacyEnabled,
            hint = "Hides app preview in app switcher",
        ),
        SettingItem(
            id = SettingIds.DOH_PROVIDER,
            label = "Encrypted DNS provider",
            value = settings.dohProvider.label,
            kind = SettingKind.CHOICE,
            options = DohProvider.entries.map { it.label },
            selected = settings.dohProvider.ordinal,
        ),
        toggle(
            SettingIds.STRICT_DOH,
            "Strict encrypted DNS",
            settings.strictDohEnabled,
            hint = "Stop loading if encrypted DNS is blocked",
        ),
        toggle(
            SettingIds.SAVE_SEARCH_HISTORY,
            "Save search history",
            settings.saveSearchHistory,
            hint = "Store recent searches locally",
        ),
        toggle(
            SettingIds.SAVE_BROWSING_HISTORY,
            "Save browsing history",
            settings.saveBrowsingHistory,
            hint = "Record visited threads in local history",
        ),
    )

private fun storageAndAboutRows(
    clearArmed: Boolean,
    imageCacheCleared: Boolean,
    backup: BackupState,
    version: AppVersion,
): List<SettingItem> =
    listOfNotNull(
        SettingItem(
            id = SettingIds.CLEAR_ACTIVITY,
            label = "Clear local activity",
            value = if (clearArmed) "Tap again to delete" else "Delete",
            kind = SettingKind.ACTION,
            hint = "Deletes browsing history, download records and cached images on this device.",
            destructive = true,
        ),
        SettingItem(
            id = SettingIds.CLEAR_IMAGE_CACHE,
            label = "Clear image cache",
            value = if (imageCacheCleared) "Cleared" else "Clear",
            kind = SettingKind.ACTION,
            destructive = true,
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
        SettingItem(
            id = SettingIds.PRIVACY,
            label = "Privacy policy",
            value = "Open",
            kind = SettingKind.ACTION,
        ),
        SettingItem(
            id = SettingIds.CONTACT,
            label = "Project on GitHub",
            value = "GitHub",
            kind = SettingKind.ACTION,
        ),
        versionRow(version),
    )

private fun settingsSections(groups: List<Pair<String, List<SettingItem>>>): List<Pair<String, List<SettingItem>>> {
    val rows = groups.flatMap { it.second }
    val appearanceIds =
        setOf(
            SettingIds.THEME,
            SettingIds.COLOR_THEME,
            SettingIds.AMOLED,
            SettingIds.FEED_COLUMNS,
            SettingIds.CATALOG_COLUMNS,
        )
    val privacyIds =
        setOf(
            SettingIds.HIDE_NSFW,
            SettingIds.COVER_VIOLENT,
            SettingIds.APP_LOCK,
            SettingIds.SCREEN_PRIVACY,
            SettingIds.DOH_PROVIDER,
            SettingIds.STRICT_DOH,
            SettingIds.SAVE_SEARCH_HISTORY,
            SettingIds.SAVE_BROWSING_HISTORY,
        )
    val aboutIds = setOf(SettingIds.PRIVACY, SettingIds.CONTACT, SettingIds.VERSION)
    return listOf(
        "Appearance" to rows.filter { it.id in appearanceIds },
        "Privacy and safety" to rows.filter { it.id in privacyIds },
        "Data and storage" to rows.filterNot { it.id in appearanceIds || it.id in privacyIds || it.id in aboutIds },
        "About" to rows.filter { it.id in aboutIds },
    )
}

/** The release, as Android's settings name it, and the TestFlight build under it. */
private fun versionRow(version: AppVersion): SettingItem? {
    if (version.name.isBlank()) return null
    return SettingItem(
        id = SettingIds.VERSION,
        label = "Version",
        value = version.name,
        kind = SettingKind.INFO,
        hint = version.build.takeUnless { it.isBlank() }?.let { "Build $it" },
    )
}

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
    hint: String? = null,
) = SettingItem(
    id = id,
    label = label,
    value = if (on) ON_LABEL else OFF_LABEL,
    kind = SettingKind.TOGGLE,
    checked = on,
    hint = hint,
)

/** SYSTEM -> "System", as Android labels it. */
private val AppThemeMode.label: String get() = name.lowercase().replaceFirstChar(Char::uppercase)

/** The privacy policy and project information live in the project's own repository. */
internal const val PRIVACY_POLICY_URL = "https://github.com/Defuuls/Orbin/blob/main/PRIVACY.md"
internal const val CONTACT_URL = "https://github.com/Defuuls/Orbin"

private fun colorThemeChoice(theme: AppColorTheme) =
    SettingItem(
        id = SettingIds.COLOR_THEME,
        label = "Color theme",
        value = theme.label,
        kind = SettingKind.CHOICE,
        options = AppColorTheme.entries.map { it.label },
        selected = theme.ordinal,
    )
