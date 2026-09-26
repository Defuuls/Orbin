package com.orbin.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FeedLayoutTest {
    private val chosen = AppSettings(unfoldedFeedColumns = 3, tabletFeedColumns = 4)

    @Test
    fun `a phone is one column however wide it turns`() {
        assertThat(feedColumns(FormFactor.PHONE, windowWidthDp = 412, chosen)).isEqualTo(1)
        assertThat(feedColumns(FormFactor.PHONE, windowWidthDp = 915, chosen)).isEqualTo(1)
    }

    @Test
    fun `a foldable is one column folded and the chosen count unfolded`() {
        assertThat(feedColumns(FormFactor.FOLDABLE, windowWidthDp = 374, chosen)).isEqualTo(1)
        assertThat(feedColumns(FormFactor.FOLDABLE, windowWidthDp = 673, chosen)).isEqualTo(3)
    }

    @Test
    fun `a tablet shows its chosen count, and one column in narrow split screen`() {
        assertThat(feedColumns(FormFactor.TABLET, windowWidthDp = 1280, chosen)).isEqualTo(4)
        assertThat(feedColumns(FormFactor.TABLET, windowWidthDp = 400, chosen)).isEqualTo(1)
    }

    @Test
    fun `both wide screens start at two columns`() {
        assertThat(feedColumns(FormFactor.FOLDABLE, windowWidthDp = 673, AppSettings.Default)).isEqualTo(2)
        assertThat(feedColumns(FormFactor.TABLET, windowWidthDp = 1280, AppSettings.Default)).isEqualTo(2)
    }

    /** A value saved on a tablet and restored onto a foldable cannot exceed what the foldable allows. */
    @Test
    fun `a saved count beyond the device's limit is capped`() {
        val tooMany = AppSettings(unfoldedFeedColumns = 4)

        assertThat(feedColumns(FormFactor.FOLDABLE, windowWidthDp = 673, tooMany)).isEqualTo(3)
    }
}
