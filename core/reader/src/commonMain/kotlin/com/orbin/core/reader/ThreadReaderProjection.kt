package com.orbin.core.reader

import com.orbin.core.model.AppSettings
import com.orbin.core.model.PostFilterRule
import com.orbin.core.model.PostId
import com.orbin.core.model.ThreadMemory
import com.orbin.core.model.UserContentFilters
import com.orbin.domain.thread.projectReplyRows
import com.orbin.uinext.screens.Post

internal class ReaderReplyGraph(
    val nested: List<Post>,
    val flat: List<Post>,
)

internal fun ThreadPresentationIndex.buildReaderReplyGraph(): ReaderReplyGraph {
    val source = "${thread.key.provider.value}/${thread.key.board.value}/${thread.key.thread.value}"
    val nested =
        projectReplyRows(thread.allPosts, source).map { row ->
            rowsById.getValue(row.postId.value.toString()).row.copy(
                stableKey = row.path,
                branchPath = row.path,
                primaryPath = row.primaryPath,
                ancestorPaths = row.ancestorPaths,
                depth = row.depth,
                replies = row.descendants.size,
                descendantIds = row.descendants.mapTo(mutableSetOf()) { it.value.toString() },
                preview = row.preview,
            )
        }
    val primary = nested.filterNot { it.preview }.associateBy { it.id }
    val flat =
        rows.distinctBy { it.row.id }.map { entry ->
            val row = primary.getValue(entry.row.id)
            val path = "$source/${row.id}"
            row.copy(stableKey = path, branchPath = path, primaryPath = path, ancestorPaths = emptyList(), depth = 0)
        }
    return ReaderReplyGraph(nested, flat)
}

/** Projects only the policy-approved snapshot; revealing a user-hidden row cannot restore blocked content. */
fun ThreadPresentationIndex.readerRows(
    nested: Boolean,
    foldedPaths: Set<String> = emptySet(),
    hiddenPostIds: Set<Long> = emptySet(),
    hiddenPosterIds: Set<String> = emptySet(),
    postFilters: List<PostFilterRule> = emptyList(),
    revealedPostIds: Set<Long> = emptySet(),
    unseenPostIds: Set<Long> = emptySet(),
): List<Post> {
    val filters = UserContentFilters(postFilters)
    val reasons =
        rows
            .mapNotNull { row ->
                val post = row.post
                val reason =
                    when {
                        post.id.value in revealedPostIds -> null
                        post.id.value in hiddenPostIds -> "Hidden by you"
                        post.poster.posterId?.let {
                            it in hiddenPosterIds
                        } == true -> "Hidden · ID:${post.poster.posterId}"
                        else -> filters.reasonFor(post)
                    }
                reason?.let { post.id to it }
            }.toMap()
    val templates = if (nested) replyGraph.nested else replyGraph.flat
    val op =
        thread.originalPost.id.value
            .toString()
    var blockedDepth: Int? = null
    return buildList {
        templates.forEach { row ->
            val blocking = blockedDepth
            if (blocking != null && row.depth > blocking) return@forEach
            blockedDepth = null
            val number = row.id.toLong()
            val hidden = reasons[PostId(number)].takeUnless { row.id == op }
            val folded = row.id != op && row.branchPath in foldedPaths
            add(
                row.copy(
                    hiddenReason = hidden,
                    unseen = number in unseenPostIds,
                    hiddenByUser = number in hiddenPostIds,
                    hiddenByPoster = row.posterId?.let { it in hiddenPosterIds } == true,
                ),
            )
            // Depth-first rows put a subtree immediately after its root, so skipping descendants
            // is one pass even for a long chain. Flat mode folds only the selected post's body.
            if (nested && (hidden != null || folded)) blockedDepth = row.depth
        }
    }
}

fun ThreadPresentationIndex.knownPostIds(): Set<Long> = rows.mapTo(mutableSetOf()) { it.post.id.value }

fun ThreadPresentationIndex.unseenPostIds(seen: Set<Long>): Set<Long> =
    knownPostIds() - seen - thread.originalPost.id.value

/** Canonical file indices survive filters; a viewer must never mistake a filtered index for an original one. */
fun ThreadPresentationIndex.visibleMediaIndices(
    settings: AppSettings,
    memory: ThreadMemory,
    revealedPostIds: Set<Long> = emptySet(),
): List<Int> {
    val visible =
        readerRows(
            nested = settings.threadLayout == com.orbin.core.model.ThreadLayout.NESTED,
            foldedPaths = memory.foldedPaths,
            hiddenPostIds = memory.hiddenPostIds,
            hiddenPosterIds = memory.hiddenPosterIds,
            postFilters = settings.postFilterRules,
            revealedPostIds = revealedPostIds,
        ).filter { it.hiddenReason == null && !it.preview && it.branchPath !in memory.foldedPaths }
            .mapTo(mutableSetOf()) { it.id }
    return attachments.indices.filter {
        val owner = postIdForMediaIndex(it)?.value
        owner?.toString() in visible || owner in revealedPostIds
    }
}
