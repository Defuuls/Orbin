package com.orbin.core.model

import kotlinx.serialization.Serializable

/** Playback choices shared by previews and the full-screen viewer on both platforms. */
@Serializable
data class PlaybackSettings(
    val autoplay: Boolean = true,
    val startMuted: Boolean = true,
    val loopVideos: Boolean = true,
    val preloadNextClip: Boolean = true,
)

/** An empty provider means app defaults; a board is always qualified by its provider. */
@Serializable
data class PlaybackScope(
    val providerId: String = "",
    val boardId: String = "",
) {
    init {
        require(boardId.isEmpty() || providerId.isNotEmpty()) { "A board scope requires a provider" }
    }

    val isApp: Boolean get() = providerId.isEmpty()
    val isBoard: Boolean get() = boardId.isNotEmpty()
    val parent: PlaybackScope? get() =
        when {
            isApp -> null
            isBoard -> PlaybackScope(providerId)
            else -> PlaybackScope()
        }
}

@Serializable
data class PlaybackOverride(
    val scope: PlaybackScope,
    val settings: PlaybackSettings,
)

/**
 * Explicit scopes contain a complete snapshot. Removing an override resumes live inheritance,
 * so later app/site changes reach every inheriting board without copying preferences around.
 */
@Serializable
data class PlaybackPreferences(
    val defaults: PlaybackSettings = PlaybackSettings(),
    val overrides: List<PlaybackOverride> = emptyList(),
) {
    fun hasOverride(scope: PlaybackScope): Boolean = !scope.isApp && overrides.any { it.scope == scope }

    fun forScope(scope: PlaybackScope): PlaybackSettings =
        if (scope.isApp) {
            defaults
        } else {
            overrides.lastOrNull { it.scope == scope }?.settings ?: forScope(requireNotNull(scope.parent))
        }

    fun resolve(
        provider: ProviderId,
        board: BoardId,
    ): PlaybackSettings = forScope(PlaybackScope(provider.value, board.value))

    /** Null clears only this scope; site resets deliberately retain explicit board overrides. */
    fun withSettings(
        scope: PlaybackScope,
        settings: PlaybackSettings?,
    ): PlaybackPreferences =
        if (scope.isApp) {
            copy(defaults = settings ?: PlaybackSettings())
        } else {
            copy(
                overrides =
                    overrides.filterNot { it.scope == scope } +
                        listOfNotNull(settings?.let { PlaybackOverride(scope, it) }),
            )
        }
}
