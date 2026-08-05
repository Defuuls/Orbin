package com.orbin.core.model

import kotlinx.serialization.Serializable

/**
 * A portable snapshot of the user data that is expensive to recreate by hand.
 *
 * The app stores everything encrypted at rest and sets `allowBackup="false"`, so Android's own
 * backup never sees this data — deliberately, since cloud backup would undermine that threat
 * model. The cost is that a reinstall wipes subscriptions and settings, which stops being
 * hypothetical the moment a bug forces one. This document is the manual alternative: the user
 * exports it to a file they control, and imports it after reinstalling.
 *
 * Every field carries a default and unknown keys are ignored on read, so a backup written by a
 * newer build still restores on an older one (dropping what it does not understand) and a backup
 * written by an older build still restores on a newer one (defaulting what it lacks).
 */
@Serializable
data class BackupDocument(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    /** ISO-8601 instant, for display when confirming an import. */
    val exportedAt: String = "",
    /** `versionName` of the build that produced the file, for support and debugging. */
    val exportedByAppVersion: String = "",
    val settings: AppSettings = AppSettings(),
    val subscribedBoards: List<BackupBoardRef> = emptyList(),
    val favoriteBoards: List<BackupBoardRef> = emptyList(),
    val bookmarks: List<BackupBookmark> = emptyList(),
) {
    companion object {
        /** Bump only for a change old builds cannot safely read; additive fields do not need it. */
        const val CURRENT_FORMAT_VERSION = 1
    }
}

/** A board identified by the provider that serves it, since board ids collide across providers. */
@Serializable
data class BackupBoardRef(
    val providerId: String,
    val boardId: String,
)

/**
 * A bookmarked thread, flattened away from [ThreadKey] and the id value classes.
 *
 * Unlike [AppSettings], which is serialized as-is because a hand-written mirror of forty
 * defaulted fields would drift, a bookmark is small enough to map explicitly — and that keeps the
 * file format from being pinned to the internal key type, which is free to be refactored.
 *
 * Reply counters are intentionally partial: [lastSeenReplyCount] is what unread math needs, while
 * the latest count and liveness are re-fetched on the next refresh. Restoring a stale "latest"
 * would show invented unread badges until the thread was opened.
 */
@Serializable
data class BackupBookmark(
    val providerId: String,
    val boardId: String,
    val threadId: Long,
    val title: String = "",
    val thumbnailUrl: String? = null,
    val createdAtMillis: Long = 0,
    val isWatched: Boolean = false,
    val lastSeenReplyCount: Int = 0,
)
