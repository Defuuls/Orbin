package com.orbin.app.pulse

import com.orbin.core.model.Thread
import com.orbin.core.model.stream.StreamIds
import com.orbin.uipulse.PulseLineUi

/**
 * The replies the reader has not seen yet. The opening post is never unread: opening the thread
 * is reading it.
 */
internal fun unreadPosts(
    thread: Thread,
    seen: Set<Long>,
): Set<Long> = thread.replies.mapNotNullTo(HashSet()) { post -> post.id.value.takeIf { it !in seen } }

/**
 * The post numbers, as the reader's seen-tracking expects them (decimal strings), among the line
 * ids on screen. Ids that are not post lines are ignored.
 */
internal fun visiblePostNumbers(ids: List<String>): Set<String> =
    ids.mapNotNullTo(HashSet()) { id -> StreamIds.parsePost(id)?.toString() }

/** [lines] with the posts in [unread] marked, so reading only re-marks lines and never rebuilds them. */
internal fun markUnread(
    lines: List<PulseLineUi>,
    unread: Set<Long>,
): List<PulseLineUi> =
    if (unread.isEmpty()) lines else lines.map { if (it.number in unread) it.copy(unread = true) else it }
