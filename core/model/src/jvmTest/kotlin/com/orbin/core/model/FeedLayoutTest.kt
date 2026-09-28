package com.orbin.core.model

import com.google.common.truth.Truth.assertThat
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
    fun `a tablet catalog shows its chosen columns, capped, and fits itself anywhere else`() {
        val six = AppSettings(tabletCatalogColumns = 6)

        assertThat(catalogColumns(FormFactor.TABLET, windowWidthDp = 1366, windowHeightDp = 800, six)).isEqualTo(6)
        assertThat(
            catalogColumns(FormFactor.TABLET, windowWidthDp = 1366, windowHeightDp = 800, AppSettings.Default),
        ).isEqualTo(4)
        assertThat(
            catalogColumns(
                FormFactor.TABLET,
                windowWidthDp = 1366,
                windowHeightDp = 800,
                AppSettings(tabletCatalogColumns = 20),
            ),
        ).isEqualTo(8)
        assertThat(catalogColumns(FormFactor.TABLET, windowWidthDp = 400, windowHeightDp = 891, six)).isNull()
        assertThat(catalogColumns(FormFactor.FOLDABLE, windowWidthDp = 673, windowHeightDp = 800, six)).isNull()
        assertThat(catalogColumns(FormFactor.PHONE, windowWidthDp = 915, windowHeightDp = 412, six)).isNull()
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
}
