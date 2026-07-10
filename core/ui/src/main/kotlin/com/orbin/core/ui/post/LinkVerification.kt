package com.orbin.core.ui.post

import androidx.compose.runtime.staticCompositionLocalOf
import com.orbin.core.model.LinkStatus

/**
 * Verifies whether a file-host link still exists, for [PostCommentText] to badge links with.
 * Implementations decide which hosts they support and whether verification is enabled at all —
 * returning [LinkStatus.UNKNOWN] simply leaves the link unmarked.
 */
fun interface LinkVerifier {
    suspend fun verify(url: String): LinkStatus
}

/**
 * The app root provides the real verifier; the null default keeps previews and tests rendering
 * plain, unbadged comments.
 */
val LocalLinkVerifier = staticCompositionLocalOf<LinkVerifier?> { null }
