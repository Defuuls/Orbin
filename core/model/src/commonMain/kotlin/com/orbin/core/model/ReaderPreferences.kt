package com.orbin.core.model

import kotlinx.serialization.Serializable

/** New accent identifiers deliberately do not reuse the removed colorTheme setting. */
@Serializable
enum class AccentTheme {
    ORBIN,
    CYAN,
    BLUE,
    INDIGO,
    VIOLET,
    PURPLE,
    FUCHSIA,
    PINK,
    ROSE,
    RED,
    AMBER,
    LIME,
    GREEN,
    EMERALD,
    TEAL,
}

@Serializable
enum class ThreadLayout { NESTED, FLAT }

/** Poster IDs belong to a thread and are kept in ThreadMemory, never in a global rule. */
@Serializable
enum class PostFilterKind { TEXT, NAME, TRIPCODE, COUNTRY }

@Serializable
data class PostFilterRule(
    val kind: PostFilterKind,
    val pattern: String,
    val regex: Boolean = false,
)

/** Durable reader choices; membership is always qualified by the complete thread address. */
@Serializable
data class ThreadMemory(
    val key: ThreadKey,
    val anchorPostId: Long? = null,
    val rowPath: String? = null,
    val offsetPx: Int = 0,
    val foldedPaths: Set<String> = emptySet(),
    val hiddenPostIds: Set<Long> = emptySet(),
    val hiddenPosterIds: Set<String> = emptySet(),
    val seenPostIds: Set<Long> = emptySet(),
    val knownPostIds: Set<Long> = emptySet(),
    val acknowledgedReplyCount: Int = 0,
    val updatedAtMillis: Long = 0,
)

@Serializable
enum class ThreadAvailability { UNKNOWN, LIVE, ARCHIVED, DELETED }
