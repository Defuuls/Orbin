package com.orbin.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class FeedLayoutTest {
    private val chosen = AppSettings(unfoldedFeedColumns = 3, tabletFeedColumns = 4)

    @Test
    fun `a phone is one column however wide it turns`() {
        assertThat(feedColumns(FormFactor.PHONE, windowWidthDp = 412, windowHeightDp = 891, chosen)).isEqualTo(1)
        assertThat(feedColumns(FormFactor.PHONE, windowWidthDp = 915, windowHeightDp = 412, chosen)).isEqualTo(1)
    }

    @Test
    fun `a foldable is one column folded and the chosen count unfolded`() {
        assertThat(feedColumns(FormFactor.FOLDABLE, windowWidthDp = 374, windowHeightDp = 891, chosen)).isEqualTo(1)
        assertThat(feedColumns(FormFactor.FOLDABLE, windowWidthDp = 673, windowHeightDp = 800, chosen)).isEqualTo(3)
    }

    @Test
    fun `a tablet shows its chosen count, and one column in narrow split screen`() {
        assertThat(feedColumns(FormFactor.TABLET, windowWidthDp = 1280, windowHeightDp = 800, chosen)).isEqualTo(4)
        assertThat(feedColumns(FormFactor.TABLET, windowWidthDp = 400, windowHeightDp = 891, chosen)).isEqualTo(1)
    }

    @Test
    fun `both wide screens start at two columns`() {
        assertThat(
            feedColumns(FormFactor.FOLDABLE, windowWidthDp = 673, windowHeightDp = 800, AppSettings.Default),
        ).isEqualTo(2)
        assertThat(
            feedColumns(FormFactor.TABLET, windowWidthDp = 1280, windowHeightDp = 800, AppSettings.Default),
        ).isEqualTo(2)
    }

    /** A value saved on a tablet and restored onto a foldable cannot exceed what the foldable allows. */
    @Test
    fun `a saved count beyond the device's limit is capped`() {
        val tooMany = AppSettings(unfoldedFeedColumns = 4)

        assertThat(feedColumns(FormFactor.FOLDABLE, windowWidthDp = 673, windowHeightDp = 800, tooMany)).isEqualTo(3)
    }

    @Test
    fun `catalog view does not change feed columns or two pane eligibility`() {
        CatalogMode.entries.forEach { mode ->
            val settings = chosen.copy(catalogMode = mode)

            assertThat(feedColumns(FormFactor.TABLET, 1280, 800, settings)).isEqualTo(4)
            assertThat(feedColumns(FormFactor.FOLDABLE, 673, 800, settings)).isEqualTo(3)
            assertThat(feedColumns(FormFactor.PHONE, 915, 412, settings)).isEqualTo(1)
            assertThat(showsTwoPanes(1280, 800)).isTrue()
            assertThat(showsTwoPanes(915, 412)).isFalse()
        }
    }

    // Galaxy Z Fold8 and Fold8 Ultra windows at Samsung's default display size (about 2.625x):
    // Fold8 cover 1248 x 1972 (10:16), inner 2448 x 1828 (4:3, wider than tall unfolded);
    // Ultra cover 1080 x 2520 (21:9), inner 2256 x 2504 (near square, taller than wide).

    @Test
    fun `a Fold8 or Fold8 Ultra cover screen is one column either way up`() {
        assertThat(feedColumns(FormFactor.FOLDABLE, windowWidthDp = 475, windowHeightDp = 751, chosen)).isEqualTo(1)
        assertThat(feedColumns(FormFactor.FOLDABLE, windowWidthDp = 751, windowHeightDp = 475, chosen)).isEqualTo(1)
        assertThat(feedColumns(FormFactor.FOLDABLE, windowWidthDp = 411, windowHeightDp = 960, chosen)).isEqualTo(1)
        assertThat(feedColumns(FormFactor.FOLDABLE, windowWidthDp = 960, windowHeightDp = 411, chosen)).isEqualTo(1)
    }

    @Test
    fun `a Fold8 or Fold8 Ultra inner screen takes the chosen columns either way up`() {
        assertThat(feedColumns(FormFactor.FOLDABLE, windowWidthDp = 933, windowHeightDp = 696, chosen)).isEqualTo(3)
        assertThat(feedColumns(FormFactor.FOLDABLE, windowWidthDp = 696, windowHeightDp = 933, chosen)).isEqualTo(3)
        assertThat(feedColumns(FormFactor.FOLDABLE, windowWidthDp = 859, windowHeightDp = 954, chosen)).isEqualTo(3)
        assertThat(feedColumns(FormFactor.FOLDABLE, windowWidthDp = 954, windowHeightDp = 859, chosen)).isEqualTo(3)
    }

    @Test
    fun `an unfolded Fold8 shows two panes held landscape, and an Ultra either way up`() {
        assertThat(showsTwoPanes(windowWidthDp = 933, windowHeightDp = 696)).isTrue()
        assertThat(showsTwoPanes(windowWidthDp = 696, windowHeightDp = 933)).isFalse()
        assertThat(showsTwoPanes(windowWidthDp = 859, windowHeightDp = 954)).isTrue()
        assertThat(showsTwoPanes(windowWidthDp = 954, windowHeightDp = 859)).isTrue()
    }

    @Test
    fun `a cover screen turned sideways never shows two panes`() {
        assertThat(showsTwoPanes(windowWidthDp = 751, windowHeightDp = 475)).isFalse()
        assertThat(showsTwoPanes(windowWidthDp = 960, windowHeightDp = 411)).isFalse()
    }

    @Test
    fun `reader pane widths match the specified windows`() {
        assertThat(readerPaneWidths(800)).isEqualTo(ReaderPaneWidths(catalogDp = 332, dividerDp = 1, threadDp = 467))
        assertThat(readerPaneWidths(960)).isEqualTo(ReaderPaneWidths(catalogDp = 399, dividerDp = 1, threadDp = 560))
        assertThat(readerPaneWidths(1280)).isEqualTo(ReaderPaneWidths(catalogDp = 420, dividerDp = 1, threadDp = 859))
    }

    @Test
    fun `reader panes fill every supported width and give the thread more space`() {
        for (width in 800..5000) {
            val panes = readerPaneWidths(width)
            assertThat(panes.catalogDp + panes.dividerDp + panes.threadDp).isEqualTo(width)
            assertThat(panes.threadDp).isGreaterThan(panes.catalogDp)
            assertThat(panes.catalogDp).isAtMost(420)
            assertThat(panes.dividerDp).isEqualTo(1)
        }
    }

    @Test
    fun `reader pane arithmetic cannot overflow at the largest integer width`() {
        val panes = readerPaneWidths(Int.MAX_VALUE)
        assertThat(panes.catalogDp).isEqualTo(420)
        assertThat(panes.catalogDp + panes.dividerDp + panes.threadDp).isEqualTo(Int.MAX_VALUE)
        assertThat(panes.threadDp).isGreaterThan(panes.catalogDp)
    }

    @Test
    fun `reader pane widths reject windows narrower than the two pane threshold`() {
        for (width in listOf(Int.MIN_VALUE, -1, 0, 1, 799)) {
            assertThrows(IllegalArgumentException::class.java) { readerPaneWidths(width) }
        }
    }
}
