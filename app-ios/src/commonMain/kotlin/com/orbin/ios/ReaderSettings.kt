package com.orbin.ios

import com.orbin.core.model.AccentTheme
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.CatalogMode
import com.orbin.core.model.DohProvider
import com.orbin.core.model.FormFactor
import com.orbin.core.model.PlaybackPreferences
import com.orbin.core.model.PlaybackScope
import com.orbin.core.model.PlaybackSettings
import com.orbin.core.model.PostFilterKind
import com.orbin.core.model.PostFilterRule
import com.orbin.core.model.ProviderId
import com.orbin.core.model.SharpPreviewsMode
import com.orbin.core.model.ThreadLayout
import com.orbin.data.database.dao.DownloadDao
import com.orbin.domain.privacy.PrivacySession
import com.orbin.domain.repository.HistoryRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.domain.repository.ThreadMemoryRepository
import com.orbin.uinext.screens.OFF_LABEL
import com.orbin.uinext.screens.ON_LABEL
import com.orbin.uinext.screens.SettingFilterRule
import com.orbin.uinext.screens.SettingItem
import com.orbin.uinext.screens.SettingKind
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
@Suppress("TooManyFunctions")
class ReaderSettings(
    private val repository: SettingsRepository,
    private val history: HistoryRepository,
    private val scope: CoroutineScope,
    private val downloadDao: DownloadDao? = null,
    private val threadMemory: ThreadMemoryRepository? = null,
) {
    val current: StateFlow<AppSettings> =
        repository.settings.stateIn(
            scope,
            SharingStarted.Eagerly,
            // Persisted autoplay may be disabled; wait for the store before starting media.
            AppSettings.Default.copy(
                playbackPreferences =
                    PlaybackPreferences(
                        defaults = PlaybackSettings(autoplay = false, preloadNextClip = false),
                    ),
            ),
        )

    fun setPlaybackSettings(
        scope: PlaybackScope,
        value: PlaybackSettings?,
    ): Job = this.scope.launch { repository.setPlaybackSettings(scope, value) }

    /** Returns true when the settings row belongs to the scoped playback controls. */
    internal fun activatePlayback(
        id: String,
        settings: AppSettings,
        scope: PlaybackScope,
    ): Boolean {
        val preferences = settings.playbackPreferences
        val playback = preferences.forScope(scope)
        val value =
            when (id) {
                "startMuted" -> playback.copy(startMuted = !playback.startMuted)
                "loopVideos" -> playback.copy(loopVideos = !playback.loopVideos)
                "inheritPlayback" -> if (preferences.hasOverride(scope)) null else playback
                "resetPlayback" -> null
                else -> return false
            }
        setPlaybackSettings(scope, value)
        return true
    }

    /** Opening a choice page is read-only; only an explicit option selection persists it. */
    internal fun selectPlaybackChoice(
        id: String,
        index: Int,
        settings: AppSettings,
        scope: PlaybackScope,
    ): Boolean {
        if (index !in 0..1) return false
        val playback = settings.playbackPreferences.forScope(scope)
        val value =
            when (id) {
                "autoplay" -> playback.copy(autoplay = index == 1)
                "preloadNextClip" -> playback.copy(preloadNextClip = index == 1)
                else -> return false
            }
        setPlaybackSettings(scope, value)
        return true
    }

    fun setHideNsfwBoards(hide: Boolean): Job = scope.launch { repository.setHideNsfwBoards(hide) }

    fun setCoverViolentMedia(cover: Boolean): Job = scope.launch { repository.setCoverViolentMedia(cover) }

    fun setOpenThreadComments(enabled: Boolean): Job = scope.launch { repository.setOpenThreadComments(enabled) }

    fun setCatalogMode(mode: CatalogMode): Job = scope.launch { repository.setCatalogMode(mode) }

    fun setSharpPreviews(mode: SharpPreviewsMode): Job = scope.launch { repository.setSharpPreviews(mode) }

    fun setThemeMode(mode: AppThemeMode): Job = scope.launch { repository.setThemeMode(mode) }

    fun setAccentTheme(theme: AccentTheme): Job = scope.launch { repository.setAccentTheme(theme) }

    fun setPostFilterRules(rules: List<PostFilterRule>): Job = scope.launch { repository.setPostFilterRules(rules) }

    internal fun commitFilter(
        id: String,
        value: List<SettingFilterRule>,
    ): Boolean {
        when (id) {
            "postFilterRules" ->
                setPostFilterRules(
                    value.mapNotNull { rule ->
                        PostFilterKind.entries.firstOrNull { it.name == rule.kind }?.let {
                            PostFilterRule(
                                it,
                                rule.pattern,
                                rule.regex,
                            )
                        }
                    },
                )
            "hiddenThreadPatterns" -> scope.launch { repository.setHiddenThreadPatterns(value.map { it.pattern }) }
            else -> return false
        }
        return true
    }

    fun setFullscreenAutoAdvance(value: Boolean): Job = scope.launch { repository.setFullscreenAutoAdvance(value) }

    fun setCollectionNewestFirst(value: Boolean): Job = scope.launch { repository.setCollectionNewestFirst(value) }

    /** Keeps the platform host's dispatch small while all values use the shared persisted setters. */
    internal fun activatePrivacy(
        id: String,
        settings: AppSettings,
    ): Boolean {
        val work: suspend () -> Unit =
            when (id) {
                "nsfwEachSession" -> {
                    { repository.setNsfwEachSession(!settings.nsfwEachSession) }
                }
                "coverNsfwMedia" -> {
                    { repository.setCoverNsfwMedia(!settings.coverNsfwMedia) }
                }
                "historySessionOnly" -> {
                    { repository.setHistorySessionOnly(!settings.historySessionOnly) }
                }
                "privateSavedFiles" -> {
                    { repository.setPrivateSavedFiles(!settings.privateSavedFiles) }
                }
                "askBeforePhotos" -> {
                    { repository.setAskBeforePhotos(!settings.askBeforePhotos) }
                }
                "showSavedMediaTab" -> {
                    { repository.setShowSavedMediaTab(!settings.showSavedMediaTab) }
                }
                "fullscreenAutoAdvance" -> {
                    { repository.setFullscreenAutoAdvance(!settings.fullscreenAutoAdvance) }
                }
                "autoScrollToLastSeen" -> {
                    { repository.setAutoScrollToLastSeen(!settings.autoScrollToLastSeen) }
                }
                "showStickyThreads" -> {
                    { repository.setShowStickyThreads(!settings.showStickyThreads) }
                }
                "nsfwReveal" -> {
                    PrivacySession.revealNsfw()
                    return true
                }
                else -> return false
            }
        scope.launch { work() }
        return true
    }

    internal fun selectAppearance(
        id: String,
        index: Int,
    ): Boolean =
        when (id) {
            "accentTheme" -> {
                AccentTheme.entries.getOrNull(index)?.let(::setAccentTheme)
                true
            }
            "threadLayout" -> {
                ThreadLayout.entries.getOrNull(index)?.let { value ->
                    scope.launch { repository.setThreadLayout(value) }
                }
                true
            }
            else -> false
        }

    fun setAmoled(amoled: Boolean): Job = scope.launch { repository.setAmoled(amoled) }

    fun setActiveProviderId(site: ProviderId): Job = scope.launch { repository.setActiveProviderId(site) }

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
            threadMemory?.clear()
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
    const val OPEN_THREAD_COMMENTS = "openThreadComments"
    const val THEME = "themeMode"
    const val AMOLED = "amoled"
    const val FEED_COLUMNS = "feedColumns"
    const val CATALOG_MODE = "catalogMode"
    const val SHARP_PREVIEWS = "sharpPreviews"
    const val APP_LOCK = "biometric"
    const val SCREEN_PRIVACY = "screenPrivacy"
    const val DOH_PROVIDER = "dohProvider"
    const val STRICT_DOH = "strictDns"
    const val SAVE_SEARCH_HISTORY = "saveSearchHistory"
    const val SAVE_BROWSING_HISTORY = "saveBrowsingHistory"
    const val CLEAR_ACTIVITY = "clearActivity"
    const val CLEAR_IMAGE_CACHE = "clearImageCache"
    const val CLEAR_MEDIA_CACHE = "clearMediaCache"
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
    version: AppVersion = AppVersion(),
    cloudSyncRows: List<SettingItem> = emptyList(),
    mediaCacheLabel: String = "Empty",
): List<Pair<String, List<SettingItem>>> =
    listOf(
        "" to (appearanceAndPrivacyRows(settings) + privacyExtras(settings)),
        "" to storageAndAboutRows(clearArmed, imageCacheCleared, backup, version, mediaCacheLabel),
        "" to cloudSyncRows,
    ).let(::settingsSections)

private fun appearanceAndPrivacyRows(settings: AppSettings): List<SettingItem> =
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
        toggle(SettingIds.AMOLED, "AMOLED black", settings.amoled),
        SettingItem(
            id = SettingIds.CATALOG_MODE,
            label = "Catalog view",
            value = settings.catalogMode.label,
            kind = SettingKind.CHOICE,
            options = CatalogMode.entries.map { it.label },
            selected = settings.catalogMode.ordinal,
        ),
        SettingItem(
            id = SettingIds.SHARP_PREVIEWS,
            label = "Sharp previews",
            value = settings.sharpPreviews.label,
            kind = SettingKind.CHOICE,
            options = SharpPreviewsMode.entries.map { it.label },
            selected = settings.sharpPreviews.ordinal,
            hint =
                "Load full-size still images in card and grid views. Videos, GIFs, and files over 4 MB " +
                    "keep the thumbnail. Wi-Fi only also stays on the thumbnail when Low Data Mode is on.",
        ),
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
        // On iOS the app's own requests always require encrypted DNS (see EncryptedDns). What this
        // adds is turning off the system players and web view, which cannot use it.
        toggle(
            SettingIds.STRICT_DOH,
            "Strict encrypted DNS",
            settings.strictDohEnabled,
            hint = STRICT_DNS_HINT,
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

private fun privacyExtras(settings: AppSettings): List<SettingItem> =
    appearanceAndSessionRows(settings) + savedMediaSettingsRows(settings) + threadReadingSettingsRows(settings)

private fun appearanceAndSessionRows(settings: AppSettings): List<SettingItem> =
    buildList {
        add(
            SettingItem(
                "accentTheme",
                "Accent color",
                settings.accentTheme.name
                    .lowercase()
                    .replaceFirstChar(Char::uppercase),
                SettingKind.CHOICE,
                options = AccentTheme.entries.map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                selected = settings.accentTheme.ordinal,
            ),
        )
        add(
            toggle(
                "nsfwEachSession",
                "Reveal NSFW each session",
                settings.nsfwEachSession,
                hint = "Require another reveal after leaving Orbin. Permanent hiding still applies.",
            ),
        )
        if (settings.nsfwEachSession && !settings.hideNsfwBoards && !settings.nsfwSessionRevealed) {
            add(SettingItem("nsfwReveal", "Show NSFW for this session", "Reveal", SettingKind.ACTION))
        }
        add(toggle("coverNsfwMedia", "Cover NSFW media until opened", settings.coverNsfwMedia))
        add(
            toggle(
                "historySessionOnly",
                "Only keep history this session",
                settings.historySessionOnly,
                hint = "Never stored or backed up; forgotten when you leave Orbin.",
            ),
        )
    }

private fun savedMediaSettingsRows(settings: AppSettings): List<SettingItem> =
    buildList {
        add(
            toggle(
                "privateSavedFiles",
                "Keep new saved files in Orbin",
                settings.privateSavedFiles,
                hint = "New saves stay in private app storage. Existing public files stay where they are.",
            ),
        )
        add(
            toggle(
                "askBeforePhotos",
                "Ask before saving to Photos",
                settings.askBeforePhotos,
                hint = "Confirm before adding media to your photo library.",
            ),
        )
        add(toggle("showSavedMediaTab", "Show the Downloads tab", settings.showSavedMediaTab))
        add(toggle("fullscreenAutoAdvance", "Play the next item when a video ends", settings.fullscreenAutoAdvance))
    }

private fun threadReadingSettingsRows(settings: AppSettings): List<SettingItem> =
    buildList {
        add(
            SettingItem(
                "threadLayout",
                "Thread layout",
                settings.threadLayout.name
                    .lowercase()
                    .replaceFirstChar(Char::uppercase),
                SettingKind.CHOICE,
                options =
                    ThreadLayout.entries.map {
                        it.name.lowercase().replaceFirstChar(Char::uppercase)
                    },
                selected = settings.threadLayout.ordinal,
            ),
        )
        add(toggle("autoScrollToLastSeen", "Open threads where you left them", settings.autoScrollToLastSeen))
        add(toggle("showStickyThreads", "Show pinned threads", settings.showStickyThreads))
        add(
            SettingItem(
                "hiddenThreadPatterns",
                "Hidden thread filters",
                settings.hiddenThreadPatterns.size.toString(),
                SettingKind.TEXT,
                filterRules = settings.hiddenThreadPatterns.map { SettingFilterRule(pattern = it) },
            ),
        )
        add(
            SettingItem(
                "postFilterRules",
                "Hidden post filters",
                settings.postFilterRules.size.toString(),
                SettingKind.TEXT,
                filterRules = settings.postFilterRules.map { SettingFilterRule(it.kind.name, it.pattern, it.regex) },
            ),
        )
    }

private fun storageAndAboutRows(
    clearArmed: Boolean,
    imageCacheCleared: Boolean,
    backup: BackupState,
    version: AppVersion,
    mediaCacheLabel: String,
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
            id = SettingIds.CLEAR_MEDIA_CACHE,
            label = "Clear cache",
            value = mediaCacheLabel,
            hint = "Cached images and video frames. Keeps browsing history, transfers and saved media.",
            kind = SettingKind.ACTION,
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
            hint =
                "Portable ZIP with settings, followed boards, bookmarks, history and saved media. " +
                    "Android can restore it too.",
        ),
        SettingItem(
            id = SettingIds.IMPORT_BACKUP,
            label = "Import data",
            value =
                when (backup) {
                    is BackupState.Imported ->
                        "Restored ${backup.boards} boards, ${backup.bookmarks} threads" +
                            if (backup.mediaFiles > 0) ", ${backup.mediaFiles} saved media files" else ""
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
            SettingIds.AMOLED,
            SettingIds.CATALOG_MODE,
            SettingIds.SHARP_PREVIEWS,
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

internal const val STRICT_DNS_HINT =
    "Load each video fully before playing and turn off 4chan web search, so nothing uses the device's DNS"
