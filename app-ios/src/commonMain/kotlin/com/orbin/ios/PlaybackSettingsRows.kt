package com.orbin.ios

import com.orbin.core.model.AppSettings
import com.orbin.core.model.PlaybackScope
import com.orbin.uinext.OFF_LABEL
import com.orbin.uinext.ON_LABEL
import com.orbin.uinext.SettingItem
import com.orbin.uinext.SettingKind

/** A scope is identified by provider and board IDs; display names are never used as keys. */
internal fun playbackRows(
    settings: AppSettings,
    choices: List<Pair<PlaybackScope, String>>,
    scope: PlaybackScope,
): List<SettingItem> {
    val preferences = settings.playbackPreferences
    val playback = preferences.forScope(scope)
    return buildList {
        add(
            SettingItem(
                id = "playbackScope",
                label = "Scope",
                value =
                    choices.firstOrNull { it.first == scope }?.second
                        ?: scope.fallbackLabel(
                            choices.firstOrNull { it.first == PlaybackScope(scope.providerId) }?.second,
                        ),
                kind = SettingKind.CHOICE,
                options = choices.map { it.second },
                selected = choices.indexOfFirst { it.first == scope },
                hint =
                    when {
                        scope.isApp -> "Changes apply to sites and boards that inherit app defaults."
                        scope.isBoard -> "Changes apply to this board."
                        else -> "Changes apply to this site and its inheriting boards."
                    },
            ),
        )
        if (!scope.isApp) {
            add(
                playbackToggle(
                    "inheritPlayback",
                    if (scope.isBoard) "Use site settings" else "Use app defaults",
                    !preferences.hasOverride(scope),
                    "Changing a control creates an override for this scope.",
                ),
            )
        }
        add(playbackToggle("autoplay", "Autoplay", playback.autoplay, "Start clips automatically."))
        add(playbackToggle("startMuted", "Start muted", playback.startMuted, "Play clips without sound."))
        add(playbackToggle("loopVideos", "Loop videos", playback.loopVideos, "Replay the active clip."))
        add(
            playbackToggle(
                "preloadNextClip",
                "Preload next clip",
                playback.preloadNextClip,
                "Load upcoming media ahead of time.",
            ),
        )
        if (!scope.isApp && preferences.hasOverride(scope)) {
            add(
                SettingItem(
                    id = "resetPlayback",
                    label = if (scope.isBoard) "Reset to site settings" else "Reset to app defaults",
                    value = "Reset",
                    kind = SettingKind.ACTION,
                ),
            )
        }
    }
}

private fun playbackToggle(
    id: String,
    label: String,
    checked: Boolean,
    hint: String,
) = SettingItem(id, label, if (checked) ON_LABEL else OFF_LABEL, checked = checked, hint = hint)

private fun PlaybackScope.fallbackLabel(siteName: String?): String =
    when {
        isApp -> "App defaults"
        isBoard -> "${siteName ?: providerId} · /$boardId/"
        else -> siteName ?: providerId
    }
