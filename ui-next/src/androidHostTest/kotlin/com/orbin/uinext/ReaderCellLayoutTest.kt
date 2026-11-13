package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h1000dp-xhdpi")
class ReaderCellLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a wide reading column never exceeds the reading maximum`() {
        composeRule.setContent {
            NextTheme {
                ReaderCellLayout(wideWindow = true, contentMaxWidth = ReaderSize.readingMax) {
                    Box(it.height(ReaderSize.touchTarget).testTag("text"))
                }
            }
        }
        val text = bounds("text")
        assertEquals(ReaderSize.readingMax.value, (text.right - text.left).value, 0.5f)
        assertEquals(ReaderSpace.gutter.value, text.left.value, 0.5f)
    }

    @Test
    fun `without a thumbnail the text takes the width the image would occupy`() {
        composeRule.setContent {
            NextTheme {
                Column {
                    ReaderCellLayout(thumbnail = { Box(it) }) { Box(it.height(48.dp).testTag("with")) }
                    ReaderCellLayout { Box(it.height(48.dp).testTag("without")) }
                }
            }
        }
        val with = bounds("with")
        val without = bounds("without")
        assertEquals(ReaderSpace.gutter.value, without.left.value, 0.5f)
        assertEquals((ReaderSize.thumbnail + ReaderSpace.gap).value, (with.left - without.left).value, 0.5f)
        assertEquals(
            (ReaderSize.thumbnail + ReaderSpace.gap).value,
            (
                (without.right - without.left) -
                    (with.right - with.left)
            ).value,
            0.5f,
        )
    }

    @Test
    fun `rtl puts the image on the right and the trailing pill on the left`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NextTheme {
                    ReaderCellLayout(
                        thumbnail = { Box(it.testTag("image")) },
                        trailing = { Box(Modifier.size(48.dp).testTag("pill")) },
                    ) { Box(it.height(48.dp).testTag("text")) }
                }
            }
        }
        val image = bounds("image")
        val text = bounds("text")
        val pill = bounds("pill")
        assertEquals(ReaderSpace.gutter.value, pill.left.value, 0.5f)
        assertEquals(1000f - ReaderSpace.gutter.value, image.right.value, 0.5f)
        assertTrue(image.left > text.right)
        assertTrue(pill.right < text.left)
    }

    @Test
    fun `font two stacks a full square above unclipped text and the trailing label below it`() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                NextTheme {
                    ReaderCellLayout(
                        thumbnail = { Box(it.testTag("image")) },
                        trailing = { Text("123", Modifier.testTag("pill"), style = OrbinType.pill) },
                    ) { Text("Reading content", it.testTag("text"), style = OrbinType.readerBody) }
                }
            }
        }
        val image = bounds("image")
        val text = bounds("text")
        val pill = bounds("pill")
        assertEquals(ReaderSize.thumbnail.value, (image.right - image.left).value, 0.5f)
        assertEquals((image.right - image.left).value, (image.bottom - image.top).value, 0.5f)
        assertTrue(text.top >= image.bottom + ReaderSpace.gapSmall)
        assertTrue(pill.top >= text.bottom + ReaderSpace.line)
        assertEquals(text.left.value, pill.left.value, 0.5f)
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
}
