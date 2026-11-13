package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The active media wall uses the same album geometry and reveal contract as thread Files. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class ReaderAlbumPresentationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun allMediaUsesCoralCardGuttersSquarePreviewsAndQualifiedSourceLabels() {
        val cells =
            listOf(
                MediaCell("fourchan/g/7/file", "fourchan /g/", details = "JPG 640x480 2 KB"),
                MediaCell("bbwchan/g/7/file", "bbwchan /g/", details = "WEBM 1920x1080 2 MB"),
            )
        var opened: MediaCell? = null
        compose.setContent {
            NextTheme {
                MediaWallScreen(1, 1, 0, cells = cells, showRail = false, onOpen = { opened = it })
            }
        }
        val first = compose.onNodeWithTag(ReaderAlbumTags.cell(cells[0].id)).getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag(ReaderAlbumTags.cell(cells[1].id)).getUnclippedBoundsInRoot()
        assertEquals(12f, first.left.value, 0.5f)
        assertEquals(12f, (second.left - first.right).value, 0.5f)
        val preview = compose.onNodeWithTag(ReaderAlbumTags.thumbnail(cells[0].id), true).getUnclippedBoundsInRoot()
        assertEquals((preview.right - preview.left).value, (preview.bottom - preview.top).value, 0.5f)
        val details = compose.onNodeWithTag(ReaderAlbumTags.details(cells[0].id), true).getUnclippedBoundsInRoot()
        assertTrue((details.bottom - details.top).value >= 40f)
        compose.onNodeWithText("fourchan /g/\nJPG 640x480 2 KB", true).assertExists()
        compose.onNodeWithTag(ReaderAlbumTags.cell(cells[1].id)).performSemanticsAction(SemanticsActions.OnClick) {
            assertTrue(it())
        }
        compose.runOnIdle { assertEquals(cells[1], opened) }
    }

    @Test
    fun coveredAllMediaDoesNotComposeTheThumbnailUntilRevealAndKeepsItsSaveAction() {
        val cell = MediaCell("bbwchan/g/7/file", "bbwchan /g/", spoiler = true, details = "JPG 640x480 2 KB")
        var previews = 0
        var opens = 0
        var saved: MediaCell? = null
        compose.setContent {
            NextTheme {
                MediaWallScreen(
                    1,
                    1,
                    0,
                    cells = listOf(cell),
                    showRail = false,
                    onOpen = { opens++ },
                    onLongPress = { saved = it },
                    tile = { _, modifier ->
                        SideEffect { previews++ }
                        Box(modifier.testTag("actual-media-preview"))
                    },
                )
            }
        }
        val tile = compose.onNodeWithTag(ReaderAlbumTags.cell(cell.id))
        compose.onNodeWithTag("actual-media-preview", true).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, previews) }
        tile.performSemanticsAction(SemanticsActions.OnLongClick) { assertTrue(it()) }
        compose.runOnIdle {
            assertEquals(cell, saved)
            assertEquals(0, opens)
            assertEquals(0, previews)
        }
        tile.performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.onNodeWithTag("actual-media-preview", true).assertExists()
        compose.runOnIdle {
            assertEquals(0, opens)
            assertTrue(previews > 0)
        }
        tile.performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.runOnIdle { assertEquals(1, opens) }
    }
}
