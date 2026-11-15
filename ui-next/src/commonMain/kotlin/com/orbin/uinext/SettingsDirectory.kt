package com.orbin.uinext

import androidx.compose.runtime.Composable
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_settings_about
import com.orbin.uinext.resources.reader_settings_downloads
import com.orbin.uinext.resources.reader_settings_enabled_sites
import com.orbin.uinext.resources.reader_settings_favorite_boards
import com.orbin.uinext.resources.reader_settings_followed_summary
import com.orbin.uinext.resources.reader_settings_help
import com.orbin.uinext.resources.reader_settings_help_summary
import com.orbin.uinext.resources.reader_settings_hidden
import com.orbin.uinext.resources.reader_settings_history
import com.orbin.uinext.resources.reader_settings_history_summary
import com.orbin.uinext.resources.reader_settings_other
import com.orbin.uinext.resources.reader_settings_other_summary
import com.orbin.uinext.resources.reader_settings_section_backup
import com.orbin.uinext.resources.reader_settings_section_boards
import com.orbin.uinext.resources.reader_settings_section_media
import com.orbin.uinext.resources.reader_settings_section_privacy
import com.orbin.uinext.resources.reader_settings_section_storage
import com.orbin.uinext.resources.reader_settings_section_theme
import com.orbin.uinext.resources.reader_settings_section_threads
import com.orbin.uinext.resources.reader_settings_sites_summary
import com.orbin.uinext.resources.reader_settings_storage_summary
import com.orbin.uinext.resources.reader_settings_view_history
import org.jetbrains.compose.resources.stringResource

internal val SETTINGS_SECTION_ORDER =
    listOf("theme", "boards", "threads", "media", "hidden", "privacy", "history", "storage", "backup", "about", "other")

/** Presentation alone groups controls; their platform IDs, values and callbacks stay intact. */
internal fun settingsSections(groups: List<Pair<String, List<SettingItem>>>): Map<String, List<SettingItem>> {
    val sections = groups.flatMap { it.second }.groupBy { settingsSectionId(it.id) }
    return SETTINGS_SECTION_ORDER.mapNotNull { id -> sections[id]?.let { id to it } }.toMap()
}

/** Unknown platform controls remain visible rather than disappearing during a redesign. */
internal fun settingsSectionId(id: String): String =
    when (id) {
        "themeMode", "amoled" -> "theme"
        "catalogMode", "feedColumns" -> "boards"
        "openThreadComments" -> "threads"
        "playbackScope", "autoplay", "startMuted", "loopVideos",
        "preloadNextClip", "inheritPlayback", "resetPlayback",
        -> "media"
        "hideNsfw", "coverViolent" -> "hidden"
        "biometric", "screenPrivacy", "dohProvider", "strictDns" -> "privacy"
        "saveSearchHistory", "saveBrowsingHistory" -> "history"
        "clearActivity", "clearImageCache", "videoCacheLimit", "clearVideoCache" -> "storage"
        "exportBackup", "importBackup" -> "backup"
        "checkUpdates", "updateOnLaunch", "version", "privacy", "contact" -> "about"
        else -> "other"
    }

internal fun settingsSectionDestinations(id: String): List<String> =
    when (id) {
        "boards" -> listOf("boards", "favorites")
        "history" -> listOf("history")
        "storage" -> listOf("downloads")
        "about" -> listOf("help")
        else -> emptyList()
    }

@Composable
internal fun settingsSectionLabels(): Map<String, String> =
    mapOf(
        "theme" to stringResource(Res.string.reader_settings_section_theme),
        "boards" to stringResource(Res.string.reader_settings_section_boards),
        "threads" to stringResource(Res.string.reader_settings_section_threads),
        "media" to stringResource(Res.string.reader_settings_section_media),
        "hidden" to stringResource(Res.string.reader_settings_hidden),
        "privacy" to stringResource(Res.string.reader_settings_section_privacy),
        "history" to stringResource(Res.string.reader_settings_history),
        "storage" to stringResource(Res.string.reader_settings_section_storage),
        "backup" to stringResource(Res.string.reader_settings_section_backup),
        "about" to stringResource(Res.string.reader_settings_about),
        "other" to stringResource(Res.string.reader_settings_other),
    )

@Composable
internal fun settingsDestinationLabel(id: String): String =
    stringResource(
        when (id) {
            "boards" -> Res.string.reader_settings_enabled_sites
            "favorites" -> Res.string.reader_settings_favorite_boards
            "history" -> Res.string.reader_settings_view_history
            "downloads" -> Res.string.reader_settings_downloads
            "help" -> Res.string.reader_settings_help
            else -> Res.string.reader_settings_other
        },
    )

@Composable
internal fun settingsDirectorySummary(id: String): String =
    stringResource(
        when (id) {
            "boards" -> Res.string.reader_settings_sites_summary
            "favorites" -> Res.string.reader_settings_followed_summary
            "history" -> Res.string.reader_settings_history_summary
            "downloads" -> Res.string.reader_settings_storage_summary
            "help" -> Res.string.reader_settings_help_summary
            else -> Res.string.reader_settings_other_summary
        },
    )
