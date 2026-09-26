package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
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
 * Android's: hiding NSFW boards, the theme and true black, and clearing what the app keeps.
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

    fun setThemeMode(mode: AppThemeMode): Job = scope.launch { repository.setThemeMode(mode) }

    fun setAmoled(amoled: Boolean): Job = scope.launch { repository.setAmoled(amoled) }

    /** Deletes the reading history, which is all the local activity iOS keeps so far. */
    fun clearActivity(): Job = scope.launch { history.clear() }
}

/** What the settings rows are called; the screen dispatches on these. */
internal object SettingIds {
    const val HIDE_NSFW = "hideNsfw"
    const val THEME = "themeMode"
    const val AMOLED = "amoled"
    const val APP_LOCK = "biometric"
    const val CLEAR_ACTIVITY = "clearActivity"
    const val CLEAR_IMAGE_CACHE = "clearImageCache"
}

/**
 * The rows, in Android's words and order: its preferences, and its data section less updates and
 * backup, which iOS does not have yet. Clearing activity takes two taps, as on
 * Android: the first arms the row ([clearArmed]).
 */
internal fun settingsGroups(
    settings: AppSettings,
    clearArmed: Boolean,
    imageCacheCleared: Boolean,
): List<Pair<String, List<SettingItem>>> =
    listOf(
        "" to
            listOf(
                toggle(SettingIds.HIDE_NSFW, "Hide NSFW boards", settings.hideNsfwBoards),
                SettingItem(
                    id = SettingIds.THEME,
                    label = "Theme",
                    value = settings.themeMode.label,
                    kind = SettingKind.CHOICE,
                    options = AppThemeMode.entries.map { it.label },
                    selected = settings.themeMode.ordinal,
                ),
                toggle(SettingIds.AMOLED, "True black", settings.amoled),
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
            ),
    )

private fun toggle(
    id: String,
    label: String,
    on: Boolean,
) = SettingItem(id, label, if (on) ON_LABEL else OFF_LABEL, SettingKind.TOGGLE)

/** SYSTEM -> "System", as Android labels it. */
private val AppThemeMode.label: String get() = name.lowercase().replaceFirstChar(Char::uppercase)
