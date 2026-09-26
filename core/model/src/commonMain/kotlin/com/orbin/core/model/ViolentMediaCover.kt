package com.orbin.core.model

import kotlinx.collections.immutable.toImmutableList

/**
 * Words that put a spoiler cover over a post's media rather than hiding the post.
 *
 * [PermanentContentFilter] removes unambiguous shock content outright. This is the tier below it:
 * violence, accidents and graphic footage that is often labelled but not always unwanted, so the
 * post stays and its media waits behind the same cover a spoilered file gets, until the reader
 * opens it. Covering, not hiding, is what lets this list be broad: a false positive costs a tap.
 *
 * Matched as whole words, like the permanent filter, so `crash` would not fire inside `crashed`
 * and `fight` not inside `firefight`; common inflections are listed instead. Words ordinary
 * elsewhere are kept as phrases (`car crash`, not `crash`, which on a tech board is about
 * software). It reads text only, so an unlabelled video still plays uncovered.
 */
object ViolentMediaCover {
    val terms: Set<String> =
        setOf(
            // Fights and assaults.
            "fight",
            "fights",
            "fighting",
            "brawl",
            "brawls",
            "beating",
            "beatdown",
            "knockout",
            "knocked out",
            "stabbed",
            "stabbing",
            "shooting",
            "shootout",
            "gunshot",
            "gunfight",
            "shot dead",
            "killed",
            "torture",
            "tortured",
            "lynching",
            "lynched",
            "hanged",
            "massacre",
            "execution",
            "executed",
            "cartel",
            "animal abuse",
            "animal cruelty",
            // Accidents.
            "accident",
            "accidents",
            "car crash",
            "plane crash",
            "train crash",
            "motorcycle crash",
            "fatal",
            "fatality",
            "fatalities",
            "dashcam",
            "run over",
            // War and police footage.
            "war footage",
            "combat footage",
            "bodycam",
            "body cam",
            "drone strike",
            "airstrike",
            "explosion",
            // Graphic content labels.
            "nsfl",
            "brutal",
            "gory",
            "blood",
            "bloody",
            "corpse",
            "corpses",
            "dead body",
            "dead bodies",
            "disturbing",
        )

    private val pattern: Regex = wholeWordPattern(terms)

    fun matches(text: String?): Boolean = !text.isNullOrBlank() && pattern.containsMatchIn(text)
}

/** Whether this post's subject, text or any filename carries a [ViolentMediaCover] term. */
fun Post.hasViolentLabel(): Boolean =
    ViolentMediaCover.matches(subject) ||
        ViolentMediaCover.matches(comment.raw) ||
        attachments.any { ViolentMediaCover.matches(it.originalFileName) }

/** This post with every attachment spoilered; the post itself, text and all, is unchanged. */
fun Post.withMediaCovered(): Post =
    if (attachments.all { it.isSpoiler }) {
        this
    } else {
        copy(attachments = attachments.map { it.copy(isSpoiler = true) }.toImmutableList())
    }

private fun Post.coveredIf(cover: Boolean): Post = if (cover || hasViolentLabel()) withMediaCovered() else this

/**
 * Covers the media of every post with a violent label. When the opening post has one, every file
 * in the thread is covered: a thread titled for fight videos is full of them, labelled or not.
 */
fun Thread.withViolentMediaCovered(): Thread {
    val wholeThread = originalPost.hasViolentLabel()
    return copy(
        originalPost = originalPost.coveredIf(wholeThread),
        replies = replies.map { it.coveredIf(wholeThread) }.toImmutableList(),
    )
}

/** As [Thread.withViolentMediaCovered], for a catalog entry and its preview replies. */
fun CatalogThread.withViolentMediaCovered(): CatalogThread {
    val wholeThread = originalPost.hasViolentLabel()
    return copy(
        originalPost = originalPost.coveredIf(wholeThread),
        previewReplies = previewReplies.map { it.coveredIf(wholeThread) }.toImmutableList(),
    )
}
