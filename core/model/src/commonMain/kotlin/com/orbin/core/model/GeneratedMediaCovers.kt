package com.orbin.core.model

import kotlinx.collections.immutable.toImmutableList

const val APP_COVER_NSFW = "nsfw"
const val APP_COVER_VIOLENCE = "violence"

/** App-generated covers are reversible without ever removing the provider's original spoiler. */
fun MediaAttachment.withGeneratedCover(
    reason: String,
    covered: Boolean,
): MediaAttachment {
    val original = sourceIsSpoiler ?: isSpoiler
    val reasons = if (covered) appCoverReasons + reason else appCoverReasons - reason
    return copy(
        isSpoiler = original || reasons.isNotEmpty(),
        sourceIsSpoiler = original.takeIf { reasons.isNotEmpty() },
        appCoverReasons = reasons,
    )
}

fun Post.withGeneratedMediaCover(
    reason: String,
    covered: Boolean,
): Post = copy(attachments = attachments.map { it.withGeneratedCover(reason, covered) }.toImmutableList())

fun Thread.withNsfwMediaCovered(covered: Boolean): Thread =
    copy(
        originalPost = originalPost.withGeneratedMediaCover(APP_COVER_NSFW, covered),
        replies = replies.map { it.withGeneratedMediaCover(APP_COVER_NSFW, covered) }.toImmutableList(),
    )
