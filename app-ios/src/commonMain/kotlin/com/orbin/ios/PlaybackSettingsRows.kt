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
            playbackToggle(
                SettingIds.OPEN_THREAD_COMMENTS,
                "Open comments from chat button",
                settings.openThreadComments,
                "Applies to every site. Off opens thread videos; on opens comments.",
            ),
        )
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
                    when {
                        preferences.hasOverride(scope) && scope.isBoard -> "Board overrides are active."
                        preferences.hasOverride(scope) -> "Site overrides are active."
                        scope.isBoard -> "This board uses site settings."
                        else -> "This site uses app defaults."
                    },
                ),
            )
        }
        add(playbackChoice("autoplay", "Autoplay", playback.autoplay, "Choose when clips start automatically."))
        add(playbackToggle("startMuted", "Start muted", playback.startMuted, "Play clips without sound."))
        add(playbackToggle("loopVideos", "Loop videos", playback.loopVideos, "Replay the active clip."))
        add(
            playbackChoice(
                "preloadNextClip",
                "Preload next clip",
                playback.preloadNextClip,
            ),
        )
        if (!scope.isApp && preferences.hasOverride(scope)) {
            add(
                SettingItem(
                    id = "resetPlayback",
                    label = if (scope.isBoard) "Reset to site settings" else "Reset to app defaults",
                    value = "",
                    kind = SettingKind.ACTION,
                ),
            )
        }
    }
}

private fun playbackChoice(
    id: String,
    label: String,
    enabled: Boolean,
    hint: String? = null,
) = SettingItem(
    id = id,
    label = label,
    value = if (enabled) ON_LABEL else OFF_LABEL,
    kind = SettingKind.CHOICE,
    options = listOf(OFF_LABEL, ON_LABEL),
    selected = if (enabled) 1 else 0,
    hint = hint,
)

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
