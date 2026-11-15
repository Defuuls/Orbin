package com.orbin.domain.thread

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
fun replyDepths(posts: List<Post>): Map<PostId, Int> {
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
fun replyCounts(posts: List<Post>): Map<PostId, Int> {
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

/** A place in a conversation. A multiply quoted post keeps one full place and other previews. */
data class ReplyRow(
    val postId: PostId,
    val path: String,
    val primaryPath: String,
    val depth: Int,
    val ancestorPaths: List<String>,
    val descendants: Set<PostId>,
    val collapsed: Boolean = false,
    val hiddenReason: String? = null,
    val preview: Boolean = false,
)

/**
 * A deterministic reader projection over earlier, known quote targets. The immutable post list
 * remains chronological for media and storage; only these rows acquire a conversation order.
 * [source] must include provider, board and thread, so row state cannot cross site boundaries.
 */
fun projectReplyRows(
    posts: List<Post>,
    source: String,
    nested: Boolean = true,
    foldedPaths: Set<String> = emptySet(),
    hiddenReasons: Map<PostId, String> = emptyMap(),
): List<ReplyRow> {
    if (posts.isEmpty()) return emptyList()
    val unique = posts.distinctBy { it.id }
    val op = unique.first().id
    val order = unique.mapIndexed { index, post -> post.id to index }.toMap()
    val ancestors = mutableMapOf<PostId, Set<PostId>>()
    val children = mutableMapOf<PostId, MutableList<PostId>>()
    val roots = mutableListOf(op)
    unique.forEachIndexed { index, post ->
        val quoted = post.repliesTo.filter { (order[it] ?: Int.MAX_VALUE) < index }.toSet()
        ancestors[post.id] = quoted + quoted.flatMap { ancestors[it].orEmpty() }
        // A quote of both a parent and its ancestor belongs below the parent just once.
        val parents = quoted.filter { parent -> quoted.none { other -> parent in ancestors[other].orEmpty() } }
        parents.forEach { parent -> children.getOrPut(parent) { mutableListOf() }.add(post.id) }
        if (post.id != op && (op in quoted || parents.none { it != op })) roots.add(post.id)
    }
    val descendants = mutableMapOf<PostId, Set<PostId>>()
    unique.asReversed().forEach { post ->
        descendants[post.id] = children[post.id].orEmpty().flatMap { listOf(it) + descendants[it].orEmpty() }.toSet()
    }
    if (!nested) {
        return unique.map { post ->
            val path = "$source/${post.id.value}"
            ReplyRow(
                post.id,
                path,
                path,
                0,
                emptyList(),
                descendants[post.id].orEmpty(),
                collapsed = post.id != op && path in foldedPaths,
                hiddenReason = hiddenReasons[post.id].takeUnless { post.id == op },
            )
        }
    }

    data class Visit(
        val id: PostId,
        val path: String,
        val ancestors: List<String>,
    )

    fun rootVisits() = roots.asReversed().map { Visit(it, "$source/${it.value}", emptyList()) }

    fun addChildren(
        stack: ArrayDeque<Visit>,
        visit: Visit,
    ) {
        if (visit.id == op) return
        children[visit.id].orEmpty().asReversed().forEach { child ->
            stack.addLast(Visit(child, "${visit.path}/${child.value}", visit.ancestors + visit.path))
        }
    }
    // Find primary places before folding, so folding never moves a post into another branch.
    val primary = mutableMapOf<PostId, String>()
    val firstPass = ArrayDeque(rootVisits())
    while (firstPass.isNotEmpty()) {
        val visit = firstPass.removeLast()
        if (visit.id !in primary) {
            primary[visit.id] = visit.path
            addChildren(firstPass, visit)
        }
    }
    val rows = mutableListOf<ReplyRow>()
    val stack = ArrayDeque(rootVisits())
    while (stack.isNotEmpty()) {
        val visit = stack.removeLast()
        val preview = primary[visit.id] != visit.path
        val hidden = hiddenReasons[visit.id].takeUnless { visit.id == op }
        val folded = visit.id != op && visit.path in foldedPaths
        rows.add(
            ReplyRow(
                visit.id,
                visit.path,
                primary.getValue(visit.id),
                visit.ancestors.size,
                visit.ancestors,
                descendants[visit.id].orEmpty(),
                folded,
                hidden,
                preview,
            ),
        )
        if (!preview && hidden == null && !folded) addChildren(stack, visit)
    }
    return rows
}
