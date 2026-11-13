package com.orbin.uinext

import androidx.compose.runtime.Composable
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_settings_about
import com.orbin.uinext.resources.reader_settings_about_summary
import com.orbin.uinext.resources.reader_settings_appearance
import com.orbin.uinext.resources.reader_settings_appearance_summary
import com.orbin.uinext.resources.reader_settings_board_preferences
import com.orbin.uinext.resources.reader_settings_board_summary
import com.orbin.uinext.resources.reader_settings_downloads
import com.orbin.uinext.resources.reader_settings_enabled_sites
import com.orbin.uinext.resources.reader_settings_favorite_boards
import com.orbin.uinext.resources.reader_settings_followed_summary
import com.orbin.uinext.resources.reader_settings_help
import com.orbin.uinext.resources.reader_settings_help_summary
import com.orbin.uinext.resources.reader_settings_hidden
import com.orbin.uinext.resources.reader_settings_hidden_summary
import com.orbin.uinext.resources.reader_settings_history
import com.orbin.uinext.resources.reader_settings_history_summary
import com.orbin.uinext.resources.reader_settings_other
import com.orbin.uinext.resources.reader_settings_other_summary
import com.orbin.uinext.resources.reader_settings_playback
import com.orbin.uinext.resources.reader_settings_playback_summary
import com.orbin.uinext.resources.reader_settings_privacy
import com.orbin.uinext.resources.reader_settings_privacy_summary
import com.orbin.uinext.resources.reader_settings_sites_summary
import com.orbin.uinext.resources.reader_settings_storage
import com.orbin.uinext.resources.reader_settings_storage_summary
import com.orbin.uinext.resources.reader_settings_title
import com.orbin.uinext.resources.reader_settings_watched
import com.orbin.uinext.resources.reader_settings_watched_summary
import org.jetbrains.compose.resources.stringResource

internal val EXTERNAL_SETTINGS_DESTINATIONS = setOf("boards", "favorites", "watched", "help")

/** Every supplied row remains reachable, including rows from future platform integrations. */
internal fun settingsPages(groups: List<Pair<String, List<SettingItem>>>): Map<String, List<SettingItem>> =
    groups.flatMap { it.second }.groupBy { item ->
        when (item.id) {
            "themeMode", "amoled", "catalogMode" -> "appearance"
            "hideNsfw", "coverViolent" -> "hidden"
            "saveSearchHistory", "saveBrowsingHistory" -> "history"
            "biometric", "screenPrivacy", "dohProvider", "strictDns" -> "privacy"
            "clearActivity", "clearImageCache", "exportBackup", "importBackup" -> "storage"
            "checkUpdates", "updateOnLaunch", "version", "privacy", "contact" -> "about"
            "playbackScope", "autoplay", "startMuted", "loopVideos",
            "preloadNextClip", "inheritPlayback", "resetPlayback", "openThreadComments",
            -> "playback"
            else -> "other"
        }
    }

internal fun settingsDirectory(
    pages: Set<String>,
    navigationAvailable: Boolean,
): List<Pair<String, List<String>>> =
    listOf(
        "settings" to
            listOfNotNull(
                "watched".takeIf { navigationAvailable },
                "boards".takeIf { navigationAvailable },
                "appearance".takeIf { it in pages },
                "boardPreferences".takeIf { navigationAvailable && "playback" in pages },
                "playback".takeIf { it in pages },
                "hidden".takeIf { it in pages },
                "privacy".takeIf { it in pages },
                "favorites".takeIf { navigationAvailable },
                "history".takeIf { it in pages },
                "storage".takeIf { it in pages },
            ),
        "about" to
            listOfNotNull(
                "help".takeIf { navigationAvailable },
                "about".takeIf { it in pages },
                "other".takeIf { it in pages },
            ),
    ).filter { it.second.isNotEmpty() }

@Composable
internal fun settingsPageLabels(): Map<String, String> =
    mapOf(
        "boards" to stringResource(Res.string.reader_settings_enabled_sites),
        "boardPreferences" to stringResource(Res.string.reader_settings_board_preferences),
        "favorites" to stringResource(Res.string.reader_settings_favorite_boards),
        "history" to stringResource(Res.string.reader_settings_history),
        "watched" to stringResource(Res.string.reader_settings_watched),
        "hidden" to stringResource(Res.string.reader_settings_hidden),
        "playback" to stringResource(Res.string.reader_settings_playback),
        "appearance" to stringResource(Res.string.reader_settings_appearance),
        "privacy" to stringResource(Res.string.reader_settings_privacy),
        "storage" to stringResource(Res.string.reader_settings_storage),
        "downloads" to stringResource(Res.string.reader_settings_downloads),
        "help" to stringResource(Res.string.reader_settings_help),
        "about" to stringResource(Res.string.reader_settings_about),
        "other" to stringResource(Res.string.reader_settings_other),
    )

@Composable
internal fun directorySectionLabel(id: String): String =
    if (id ==
        "settings"
    ) {
        stringResource(Res.string.reader_settings_title)
    } else {
        stringResource(Res.string.reader_settings_about)
    }

@Composable
internal fun settingsDirectorySummary(id: String): String =
    stringResource(
        when (id) {
            "boards" -> Res.string.reader_settings_sites_summary
            "boardPreferences" -> Res.string.reader_settings_board_summary
            "favorites" -> Res.string.reader_settings_followed_summary
            "history" -> Res.string.reader_settings_history_summary
            "watched" -> Res.string.reader_settings_watched_summary
            "hidden" -> Res.string.reader_settings_hidden_summary
            "playback" -> Res.string.reader_settings_playback_summary
            "appearance" -> Res.string.reader_settings_appearance_summary
            "privacy" -> Res.string.reader_settings_privacy_summary
            "storage", "downloads" -> Res.string.reader_settings_storage_summary
            "help" -> Res.string.reader_settings_help_summary
            "about" -> Res.string.reader_settings_about_summary
            else -> Res.string.reader_settings_other_summary
        },
    )
