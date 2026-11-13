package com.orbin.feature.gallery

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.orbin.core.model.MediaAttachment
import kotlin.math.abs

internal data class GalleryPagerSelection(
    val state: PagerState,
    val activePageKey: String,
) {
    fun activeIndexIn(content: GalleryContentState.Ready): Int =
        content.pageKeys.indexOf(activePageKey).coerceIn(0, content.media.lastIndex)
}

/**
 * Page indices can change when a pending thread supplies earlier files. Keep player ownership
 * attached to the visible file's key while the pager moves to its newly resolved position.
 */
@Composable
internal fun rememberGalleryPagerSelection(
    content: GalleryContentState.Ready,
    initialPage: Int,
): GalleryPagerSelection {
    val firstPage = initialPage.coerceIn(0, content.media.lastIndex)
    val pager = rememberPagerState(initialPage = firstPage, pageCount = { content.media.size })
    var activeKey by rememberSaveable { mutableStateOf(content.pageKey(firstPage)) }
    LaunchedEffect(pager, content.pageKeys, content.isThreadResolved) {
        val rememberedPage = content.pageKeys.indexOf(activeKey)
        // After activity recreation the route can seed A while the reader's saved page is B.
        // Preserve B until the thread supplies it, without starting the unrelated seeded clip.
        if (rememberedPage < 0 && !content.isThreadResolved) return@LaunchedEffect
        val anchor = rememberedPage.takeIf { it >= 0 } ?: firstPage
        activeKey = content.pageKey(anchor)
        pager.scrollToPage(anchor)
        snapshotFlow { pager.settledPage to pager.isScrollInProgress }.collect { (page, scrolling) ->
            if (!scrolling) content.pageKeys.getOrNull(page)?.let { activeKey = it }
        }
    }
    return GalleryPagerSelection(pager, activeKey)
}

@Composable
internal fun GalleryMediaPager(
    content: GalleryContentState.Ready,
    selection: GalleryPagerSelection,
    modifier: Modifier = Modifier,
    pageContent: @Composable (item: MediaAttachment, isActive: Boolean, isNear: Boolean) -> Unit,
) {
    val activeIndex = selection.activeIndexIn(content)
    VerticalPager(
        state = selection.state,
        key = content::pageKey,
        modifier = modifier,
        beyondViewportPageCount = 0,
    ) { page ->
        val item = content.media[page.coerceIn(0, content.media.lastIndex)]
        pageContent(item, content.pageKey(page) == selection.activePageKey, abs(page - activeIndex) <= 1)
    }
}
