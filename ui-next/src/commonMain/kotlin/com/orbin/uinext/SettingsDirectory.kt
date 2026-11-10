package com.orbin.uinext

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.social_settings_about
import com.orbin.uinext.resources.social_settings_appearance
import com.orbin.uinext.resources.social_settings_board_preferences
import com.orbin.uinext.resources.social_settings_browsing
import com.orbin.uinext.resources.social_settings_downloads
import com.orbin.uinext.resources.social_settings_enabled_sites
import com.orbin.uinext.resources.social_settings_favorite_boards
import com.orbin.uinext.resources.social_settings_help
import com.orbin.uinext.resources.social_settings_hidden
import com.orbin.uinext.resources.social_settings_history
import com.orbin.uinext.resources.social_settings_media
import com.orbin.uinext.resources.social_settings_more
import com.orbin.uinext.resources.social_settings_other
import com.orbin.uinext.resources.social_settings_playback
import com.orbin.uinext.resources.social_settings_privacy
import com.orbin.uinext.resources.social_settings_sites
import com.orbin.uinext.resources.social_settings_storage
import com.orbin.uinext.resources.social_settings_watched
import org.jetbrains.compose.resources.stringResource

internal val EXTERNAL_SETTINGS_DESTINATIONS = setOf("boards", "favorites", "watched", "help")

/** Every supplied row remains reachable, including rows from future platform integrations. */
internal fun settingsPages(groups: List<Pair<String, List<SettingItem>>>): Map<String, List<SettingItem>> =
    groups.flatMap { it.second }.groupBy { item ->
        when (item.id) {
            "themeMode", "colorTheme", "feedColumns", "catalogColumns", "amoled" -> "appearance"
            "hideNsfw", "coverViolent" -> "hidden"
            "saveSearchHistory", "saveBrowsingHistory" -> "history"
            "biometric", "screenPrivacy", "dohProvider", "strictDns" -> "privacy"
            "clearActivity", "clearImageCache", "exportBackup", "importBackup" -> "storage"
            "checkUpdates", "updateOnLaunch", "version", "privacy", "contact" -> "about"
            "playbackScope", "autoplay", "startMuted", "loopVideos",
            "preloadNextClip", "inheritPlayback", "resetPlayback",
            -> "playback"
            else -> "other"
        }
    }

internal fun settingsDirectory(
    pages: Set<String>,
    navigationAvailable: Boolean,
): List<Pair<String, List<String>>> =
    listOf(
        "sites" to
            if (navigationAvailable) {
                listOfNotNull(
                    "boards",
                    "boardPreferences".takeIf {
                        "playback" in pages
                    },
                    "favorites",
                )
            } else {
                emptyList()
            },
        "browsing" to
            listOfNotNull(
                "history".takeIf { it in pages },
                "watched".takeIf { navigationAvailable },
                "hidden".takeIf {
                    it in
                        pages
                },
            ),
        "media" to listOf("playback", "appearance", "privacy", "storage").filter { it in pages },
        "more" to
            listOfNotNull(
                "help".takeIf { navigationAvailable },
                "about".takeIf { it in pages },
                "other".takeIf {
                    it in
                        pages
                },
            ),
    ).filter { it.second.isNotEmpty() }

@Composable
internal fun settingsPageLabels(): Map<String, String> =
    mapOf(
        "boards" to stringResource(Res.string.social_settings_enabled_sites),
        "boardPreferences" to stringResource(Res.string.social_settings_board_preferences),
        "favorites" to stringResource(Res.string.social_settings_favorite_boards),
        "history" to stringResource(Res.string.social_settings_history),
        "watched" to stringResource(Res.string.social_settings_watched),
        "hidden" to stringResource(Res.string.social_settings_hidden),
        "playback" to stringResource(Res.string.social_settings_playback),
        "appearance" to stringResource(Res.string.social_settings_appearance),
        "privacy" to stringResource(Res.string.social_settings_privacy),
        "storage" to stringResource(Res.string.social_settings_storage),
        "downloads" to stringResource(Res.string.social_settings_downloads),
        "help" to stringResource(Res.string.social_settings_help),
        "about" to stringResource(Res.string.social_settings_about),
        "other" to stringResource(Res.string.social_settings_other),
    )

@Composable
internal fun directorySectionLabel(id: String): String =
    stringResource(
        when (id) {
            "sites" -> Res.string.social_settings_sites
            "browsing" -> Res.string.social_settings_browsing
            "media" -> Res.string.social_settings_media
            else -> Res.string.social_settings_more
        },
    )

internal fun settingsIcon(id: String): ImageVector =
    when (id) {
        "boards" -> ReaderIcons.Globe
        "boardPreferences" -> ReaderIcons.Settings
        "favorites" -> ReaderIcons.Star
        "history" -> ReaderIcons.Clock
        "watched" -> ReaderIcons.Watched
        "hidden" -> ReaderIcons.Hide
        "playback" -> ReaderIcons.Play
        "appearance" -> ReaderIcons.Palette
        "privacy" -> ReaderIcons.Shield
        "storage" -> ReaderIcons.Storage
        "downloads" -> ReaderIcons.Download
        "help" -> ReaderIcons.Help
        "about" -> ReaderIcons.Info
        else -> ReaderIcons.Saved
    }
