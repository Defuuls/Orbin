package com.orbin.uinext.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_scroll_to_bottom
import com.orbin.uinext.resources.reader_scroll_to_top
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

object ReaderScrollJumpTags {
    const val CONTROLS = "reader-scroll-jump"
    const val TOP = "reader-scroll-jump-top"
    const val BOTTOM = "reader-scroll-jump-bottom"
}

/**
 * What the jump controls read from, and drive on, a lazy list or a lazy grid. Both are adapted
 * by [rememberScrollJumpTarget], so the visibility rules and the jumps live in one place.
 */
@Stable
internal class ScrollJumpTarget(
    val scrollable: ScrollableState,
    private val firstVisibleIndex: () -> Int,
    private val firstVisibleOffset: () -> Int,
    private val visibleCount: () -> Int,
    private val itemCount: () -> Int,
    private val viewportHeight: () -> Int,
    private val animateToItem: suspend (Int) -> Unit,
) {
    /** The first visible item and how far it is scrolled; changes whenever the content moves. */
    fun position(): Pair<Int, Int> = firstVisibleIndex() to firstVisibleOffset()

    fun visibility(active: Boolean): ScrollJumpVisibility =
        scrollJumpVisibility(
            firstVisibleIndex = firstVisibleIndex(),
            visibleItemCount = visibleCount(),
            canScrollForward = scrollable.canScrollForward,
            active = active,
        )

    suspend fun jumpToTop() {
        if (itemCount() > 0) animateToItem(0)
    }

    /**
     * Ends at the true end of the content. Aligning the last item to the top stops short when that
     * item is taller than what is left of the viewport (a long final post, the bottom padding), so
     * the rest is scrolled explicitly, a bounded number of viewports at most.
     */
    suspend fun jumpToBottom() {
        val count = itemCount()
        if (count == 0) return
        animateToItem(count - 1)
        repeat(BOTTOM_SETTLE_PASSES) {
            val step = viewportHeight()
            if (!scrollable.canScrollForward || step <= 0) return
            scrollable.scrollBy(step.toFloat())
        }
    }
}

internal data class ScrollJumpVisibility(
    val top: Boolean,
    val bottom: Boolean,
) {
    val any: Boolean get() = top || bottom

    companion object {
        val HIDDEN = ScrollJumpVisibility(top = false, bottom = false)
    }
}

/**
 * The controls show only while the content is moving (or moved within the linger time, [active]) and is at least
 * a screen's worth of items away from the top. "Top" is then always useful; "bottom" hides once
 * nothing is left below. Item counts stand in for distance, which works the same for grid cells
 * and list rows without measuring unseen items.
 */
internal fun scrollJumpVisibility(
    firstVisibleIndex: Int,
    visibleItemCount: Int,
    canScrollForward: Boolean,
    active: Boolean,
): ScrollJumpVisibility {
    val screenFromTop = visibleItemCount > 0 && firstVisibleIndex >= visibleItemCount
    if (!active || !screenFromTop) return ScrollJumpVisibility.HIDDEN
    return ScrollJumpVisibility(top = true, bottom = canScrollForward)
}

@Composable
internal fun rememberScrollJumpTarget(state: LazyListState): ScrollJumpTarget =
    remember(state) {
        ScrollJumpTarget(
            scrollable = state,
            firstVisibleIndex = { state.firstVisibleItemIndex },
            firstVisibleOffset = { state.firstVisibleItemScrollOffset },
            visibleCount = { state.layoutInfo.visibleItemsInfo.size },
            itemCount = { state.layoutInfo.totalItemsCount },
            viewportHeight = { state.layoutInfo.viewportSize.height },
            animateToItem = { state.animateScrollToItem(it) },
        )
    }

@Composable
internal fun rememberScrollJumpTarget(state: LazyGridState): ScrollJumpTarget =
    remember(state) {
        ScrollJumpTarget(
            scrollable = state,
            firstVisibleIndex = { state.firstVisibleItemIndex },
            firstVisibleOffset = { state.firstVisibleItemScrollOffset },
            visibleCount = { state.layoutInfo.visibleItemsInfo.size },
            itemCount = { state.layoutInfo.totalItemsCount },
            viewportHeight = { state.layoutInfo.viewportSize.height },
            animateToItem = { state.animateScrollToItem(it) },
        )
    }

/**
 * A small up/down pair floating at the bottom end of a scrolling screen, shared by Android and
 * iOS. It appears while scrolling far from the top and fades [SCROLL_JUMP_LINGER_MILLIS] after
 * the content stops moving. Place it last in the box that holds the list; [modifier] carries the
 * screen's insets so it clears the tab bar, the home indicator and any edge control.
 */
@Composable
internal fun BoxScope.ReaderScrollJumpButtons(
    target: ScrollJumpTarget,
    modifier: Modifier = Modifier,
) {
    // Movement rather than isScrollInProgress: a jump can start and settle within one frame, which
    // composition would never see, while every drag, fling and jump moves the first item.
    var active by remember(target) { mutableStateOf(false) }
    LaunchedEffect(target) {
        snapshotFlow { target.position() }.drop(1).collectLatest {
            active = true
            delay(SCROLL_JUMP_LINGER_MILLIS)
            active = false
        }
    }
    val visibility by remember(target) { derivedStateOf { target.visibility(active) } }
    val scope = rememberCoroutineScope()
    AnimatedVisibility(
        visible = visibility.any,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.align(Alignment.BottomEnd),
    ) {
        Column(
            Modifier.testTag(ReaderScrollJumpTags.CONTROLS),
            verticalArrangement = Arrangement.spacedBy(ReaderSpace.gapSmall),
        ) {
            // Each button fades on its own, so "bottom" leaving at the end does not cut "top".
            AnimatedVisibility(visible = visibility.top, enter = fadeIn(), exit = fadeOut()) {
                ScrollJumpButton(
                    icon = ReaderIcons.ScrollTop,
                    label = stringResource(Res.string.reader_scroll_to_top),
                    tag = ReaderScrollJumpTags.TOP,
                ) { scope.launch { target.jumpToTop() } }
            }
            AnimatedVisibility(visible = visibility.bottom, enter = fadeIn(), exit = fadeOut()) {
                ScrollJumpButton(
                    icon = ReaderIcons.ScrollBottom,
                    label = stringResource(Res.string.reader_scroll_to_bottom),
                    tag = ReaderScrollJumpTags.BOTTOM,
                ) { scope.launch { target.jumpToBottom() } }
            }
        }
    }
}

@Composable
private fun ScrollJumpButton(
    icon: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    val colors = reader
    Box(
        Modifier
            .size(ReaderSize.touchTarget)
            .testTag(tag)
            .shadow(ReaderSize.cardElevation, CircleShape)
            .clip(CircleShape)
            .background(colors.appBar)
            .border(BorderStroke(ReaderSize.hairline, colors.hairline), CircleShape)
            .nextClickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onAppBar, modifier = Modifier.size(ReaderSize.icon))
    }
}

internal const val SCROLL_JUMP_LINGER_MILLIS = 2_000L
private const val BOTTOM_SETTLE_PASSES = 4
