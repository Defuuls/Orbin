package com.orbin.media.video

import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType

/**
 * Whether [attachment] may start playing by itself in a feed row, given that autoplay is
 * [autoplayEnabled] for this surface (in the full client a setting, in Orbin Minimal always true)
 * and the row is on screen.
 *
 * A spoilered video never autoplays. Playback would hand over precisely what the spoiler exists to
 * withhold, and it would do it without anyone tapping — the one interaction a spoiler asks for.
 * Such an attachment falls through to the static thumbnail, which blacks it out.
 *
 * Both apps ask this question about the same feed, so they ask it here rather than each writing
 * the condition out: the full client's feed had the type check but not the spoiler check, and got
 * the answer wrong for exactly the attachment where being wrong matters.
 */
fun canAutoplayInFeed(
    attachment: MediaAttachment,
    autoplayEnabled: Boolean,
): Boolean = autoplayEnabled && attachment.type == MediaType.VIDEO && !attachment.isSpoiler
