package com.orbin.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * A portable snapshot of the user data that is expensive to recreate by hand.
 *
 * The app stores everything encrypted at rest and sets `allowBackup="false"`, so Android's own
 * backup never sees this data — deliberately, since cloud backup would undermine that threat
 * model. The cost is that a reinstall wipes subscriptions and settings, which stops being
 * hypothetical the moment a bug forces one. This document is the manual alternative: the user
 * exports it to a file they control, and imports it after reinstalling.
 *
 * Every field carries a default and unknown keys are ignored on read. Older backups default what
 * they lack, except those containing a removed color theme: [decodeBackupDocument] refuses those
 * before any part of the backup can be applied.
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
    val savedSearches: List<BackupSavedSearch> = emptyList(),
    val history: List<BackupHistoryEntry> = emptyList(),
    val threadMemory: List<ThreadMemory> = emptyList(),
    val savedMedia: List<MediaLibraryRecord> = emptyList(),
) {
    companion object {
        /** Bump only for a change old builds cannot safely read; additive fields do not need it. */
        const val CURRENT_FORMAT_VERSION = 1
    }
}

/** Validates compatibility before deserializing settings or allowing a restore to mutate data. */
fun Json.decodeBackupDocument(contents: String): BackupDocument {
    val document = parseToJsonElement(contents)
    val settings = (document as? JsonObject)?.get("settings") as? JsonObject
    require(settings?.keys?.any { it == "colorTheme" || it == "color_theme" } != true) {
        "This backup was made by an older version of Orbin and cannot be restored."
    }
    return decodeFromJsonElement(BackupDocument.serializer(), document)
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
 * Reply counts and status retain their last observed values; refreshing checks the live source.
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
    val latestReplyCount: Int = 0,
    val isThreadDead: Boolean = false,
    val availability: ThreadAvailability = ThreadAvailability.UNKNOWN,
    val statusCheckedAtMillis: Long = 0,
)

/**
 * A saved search, flattened away from [SavedSearch] and its nested filters.
 *
 * `id` is not carried: it is a local autoincrement key, and reusing it on import would collide
 * with rows the destination install already has. Restored searches get fresh ids.
 */
@Serializable
data class BackupSavedSearch(
    val text: String,
    val boardId: String? = null,
    val mediaOnly: Boolean = false,
    val minReplies: Int? = null,
    val includeNsfw: Boolean = true,
    val contentTypes: List<String> = emptyList(),
    val createdAtMillis: Long = 0,
)

/** Persistent history only; session-only visits never enter exports or cloud records. */
@Serializable
data class BackupHistoryEntry(
    val key: ThreadKey,
    val title: String = "",
    val thumbnailUrl: String? = null,
    val lastVisitedMillis: Long = 0,
    val lastReadPostId: Long? = null,
    val lastReadOffsetPx: Int = 0,
)

fun HistoryEntry.toBackupHistory(): BackupHistoryEntry =
    BackupHistoryEntry(
        key,
        title,
        thumbnailUrl,
        lastVisitedMillis,
        lastReadPostId?.value,
        lastReadOffsetPx,
    )

fun BackupHistoryEntry.toHistoryEntry(): HistoryEntry =
    HistoryEntry(
        key,
        title,
        thumbnailUrl,
        lastVisitedMillis,
        lastReadPostId?.let(::PostId),
        lastReadOffsetPx,
    )

/** SHA-256 and byte counts cover every archive entry except this manifest itself. */
@Serializable
data class BackupArchiveManifest(
    val formatVersion: Int = 1,
    val entries: List<BackupArchiveEntry>,
)

@Serializable
data class BackupArchiveEntry(
    val path: String,
    val size: Long,
    val sha256: String,
)
