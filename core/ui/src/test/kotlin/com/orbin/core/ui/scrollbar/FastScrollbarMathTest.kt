package com.orbin.core.ui.scrollbar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The scrollbar's arithmetic, which is the half of it that can be tested without a device: where
 * the thumb sits for a given scroll position, and which item a press at a given height means.
 */
class FastScrollbarMathTest {
    private val track = 1000f
    private val minThumb = 48f

    @Test
    fun `a list that fits on screen gets no scrollbar`() {
        assertThat(metrics(totalItems = 10, visibleItems = 10, firstVisibleIndex = 0)).isNull()
    }

    @Test
    fun `an unmeasured track gets no scrollbar`() {
        assertThat(
            scrollbarMetrics(
                totalItems = 100,
                visibleItems = 10,
                firstVisibleIndex = 0,
                trackPx = 0f,
                minThumbPx = minThumb,
            ),
        ).isNull()
    }

    @Test
    fun `the thumb is sized by the fraction of the list on screen`() {
        val m = metrics(totalItems = 100, visibleItems = 10, firstVisibleIndex = 0)!!
        assertThat(m.thumbPx).isEqualTo(100f)
    }

    /** A thread with hundreds of replies would otherwise leave a sliver too small to press. */
    @Test
    fun `the thumb never shrinks below the minimum`() {
        val m = metrics(totalItems = 10_000, visibleItems = 10, firstVisibleIndex = 0)!!
        assertThat(m.thumbPx).isEqualTo(minThumb)
    }

    @Test
    fun `the thumb starts at the top and ends flush with the bottom`() {
        val top = metrics(totalItems = 100, visibleItems = 10, firstVisibleIndex = 0)!!
        assertThat(top.thumbOffsetPx).isEqualTo(0f)

        val bottom = metrics(totalItems = 100, visibleItems = 10, firstVisibleIndex = 90)!!
        assertThat(bottom.thumbOffsetPx + bottom.thumbPx).isEqualTo(track)
    }

    @Test
    fun `the thumb sits proportionally through the list`() {
        val m = metrics(totalItems = 100, visibleItems = 10, firstVisibleIndex = 45)!!
        // Half of the 90 scrollable items, across the 900px the thumb can travel.
        assertThat(m.thumbOffsetPx).isEqualTo(450f)
    }

    @Test
    fun `a press at the top of the track scrolls to the first item`() {
        assertThat(targetIndex(centreYPx = 0f)).isEqualTo(0)
    }

    @Test
    fun `a press at the bottom of the track scrolls to the last scrollable item`() {
        assertThat(targetIndex(centreYPx = track)).isEqualTo(90)
    }

    @Test
    fun `a press in the middle lands mid-list`() {
        // The press is read as the thumb's centre, so halfway down the travel is halfway through.
        assertThat(targetIndex(centreYPx = 450f + 50f)).isEqualTo(45)
    }

    @Test
    fun `a press past either end is clamped rather than overscrolling`() {
        assertThat(targetIndex(centreYPx = -500f)).isEqualTo(0)
        assertThat(targetIndex(centreYPx = track * 3)).isEqualTo(90)
    }

    @Test
    fun `a press on a list that cannot scroll stays at the top`() {
        assertThat(
            targetIndexForThumbCentre(
                centreYPx = 500f,
                trackPx = track,
                thumbPx = track,
                totalItems = 10,
                visibleItems = 10,
            ),
        ).isEqualTo(0)
    }

    private fun metrics(
        totalItems: Int,
        visibleItems: Int,
        firstVisibleIndex: Int,
    ) = scrollbarMetrics(
        totalItems = totalItems,
        visibleItems = visibleItems,
        firstVisibleIndex = firstVisibleIndex,
        trackPx = track,
        minThumbPx = minThumb,
    )

    private fun targetIndex(centreYPx: Float) =
        targetIndexForThumbCentre(
            centreYPx = centreYPx,
            trackPx = track,
            thumbPx = 100f,
            totalItems = 100,
            visibleItems = 10,
        )
}
