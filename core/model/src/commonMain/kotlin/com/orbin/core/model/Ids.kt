package com.orbin.core.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/*
 * Strongly-typed identifiers. Using value classes instead of raw String/Long makes it
 * impossible to accidentally pass a board id where a thread id is expected, with zero runtime
 * overhead.
 */

/** Identifies a provider/engine instance, e.g. "vichan-example" or "4chan". */
@Serializable
@JvmInline
value class ProviderId(
    val value: String,
)

/** A board slug within a provider, e.g. "g", "a", "tech". */
@Serializable
@JvmInline
value class BoardId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "BoardId must not be blank" }
    }
}

/** A thread number. Threads are identified by the post number of their opening post. */
@Serializable
@JvmInline
value class ThreadId(
    val value: Long,
)

/** A post number, unique within a board. */
@Serializable
@JvmInline
value class PostId(
    val value: Long,
)

/**
 * Globally-unique address of a thread across the whole app. Combines provider + board + thread,
 * which is what bookmarks, history and watchers key on.
 */
@Serializable
data class ThreadKey(
    val provider: ProviderId,
    val board: BoardId,
    val thread: ThreadId,
)

/** Globally-unique address of a single post. */
@Serializable
data class PostKey(
    val provider: ProviderId,
    val board: BoardId,
    val post: PostId,
)
