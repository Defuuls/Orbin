package com.orbin.feature.thread

import com.orbin.core.model.Post
import com.orbin.core.model.PostId

/**
 * How far down a chain of replies each post sits.
 *
 * The thread model is flat: posts arrive in the order they were made, and the only record of who
 * answered whom is `repliesTo`, the quote links inside the text. The reader has never shown that
 * structure — it draws one chronological list of cards, leaving the reader to follow quote links
 * by hand to work out what a post is answering.
 *
 * This derives a depth so the new reader can indent by it. Order is deliberately untouched: an
 * imageboard thread is read in the order it was written, and re-ordering into a tree would break
 * that for the sake of the indent.
 *
 * The rules, and why:
 *
 *  - A post quoting nothing, or quoting only the opening post, is at depth zero. Quoting the OP is
 *    how people address the thread itself, so treating it as nesting would indent most of the
 *    thread by one for no information.
 *  - Otherwise the depth is one past the *shallowest* post it quotes. Shallowest rather than
 *    deepest because a post answering both a deep sub-thread and a top-level post belongs to the
 *    conversation it rejoins, not the one it left.
 *  - Only earlier posts count. A quote can point forward (an edit, or a cross-thread link that
 *    happens to match an id) and depth has to stay well-defined, so a forward or unknown target is
 *    ignored rather than trusted.
 */
internal fun replyDepths(posts: List<Post>): Map<PostId, Int> {
    val depths = LinkedHashMap<PostId, Int>(posts.size)
    val opId = posts.firstOrNull()?.id
    posts.forEach { post ->
        val parents =
            post.repliesTo
                .filter { target -> target != opId && depths.containsKey(target) }
                .mapNotNull { target -> depths[target] }
        depths[post.id] = if (parents.isEmpty()) 0 else parents.min() + 1
    }
    return depths
}

/**
 * How many posts quote each post — the count the reader shows beside a post number.
 *
 * Derived from `repliesTo` rather than read from `backlinks` so it holds for a saved copy, where
 * the backlinks the data layer would have filled in may not have been persisted.
 */
internal fun replyCounts(posts: List<Post>): Map<PostId, Int> {
    val counts = HashMap<PostId, Int>(posts.size)
    val known = posts.mapTo(HashSet()) { it.id }
    posts.forEach { post ->
        post.repliesTo.toSet().forEach { target ->
            if (target in known && target != post.id) {
                counts[target] = (counts[target] ?: 0) + 1
            }
        }
    }
    return counts
}
