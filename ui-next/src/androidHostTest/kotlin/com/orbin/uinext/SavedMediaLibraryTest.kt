package com.orbin.uinext

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class SavedMediaLibraryTest {
    @get:Rule val compose = createComposeRule()
    private val image = SavedMediaItem("fourchan/g/1", "same.jpg", "4chan · /g/", SavedMediaKind.IMAGE, 100, 1)
    private val video = SavedMediaItem("bbwchan/g/1", "same.webm", "BBWChan · /g/", SavedMediaKind.VIDEO, 200, 2)

    @Test
    fun identitySelectionSurvivesFiltersAndDeleteWaitsForConfirmation() {
        var shared = emptyList<String>()
        var deleted = emptyList<String>()
        compose.setContent {
            var selection by remember { mutableStateOf(emptySet<String>()) }
            NextTheme {
                SavedMediaLibraryScreen(
                    listOf(image, video),
                    selection,
                    { selection = it },
                    {},
                    { shared = it },
                    null,
                    { deleted = it },
                )
            }
        }
        compose.onNodeWithText("Select files").performClick()
        compose.onNodeWithTag("orbin-saved-media:${image.id}").performClick()
        compose.onNodeWithText("Videos").performClick()
        compose.onNodeWithTag("orbin-saved-media:${image.id}").assertDoesNotExist()
        compose.onNodeWithText("Share", substring = false).performClick()
        assertEquals(listOf(image.id), shared)
        compose.onNodeWithText("Delete", substring = false).performClick()
        assertEquals(emptyList<String>(), deleted)
        compose.onNodeWithText("Delete saved files?").assertIsDisplayed()
        compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf(image.id), deleted)
    }

    @Test
    fun coveredTilesDoNotComposeTheirNativeThumbnailUntilDeliberatelyRevealed() {
        compose.setContent {
            NextTheme {
                SavedMediaLibraryScreen(
                    listOf(image.copy(covered = true)),
                    emptySet(),
                    {},
                    {},
                    null,
                    null,
                    {},
                    thumbnail = { _, _ -> Text("Native offline preview") },
                )
            }
        }
        compose.onNodeWithText("Native offline preview").assertDoesNotExist()
        compose.onNodeWithText("Spoiler").performClick()
        compose.onNodeWithText("Native offline preview").assertIsDisplayed()
    }

    @Test
    fun searchAndOrderingKeepQualifiedIdentitiesAndDoNotMutateInput() {
        val source = listOf(video, image)
        assertEquals(
            listOf(
                image.id,
            ),
            visibleSavedMedia(source, "/g/", SavedMediaKind.IMAGE, SavedMediaOrder.NEWEST).map {
                it.id
            },
        )
        assertEquals(listOf(video.id), visibleSavedMedia(source, "bbwchan", null, SavedMediaOrder.NAME).map { it.id })
        assertEquals(
            listOf(image.id, video.id),
            visibleSavedMedia(source, "same", null, SavedMediaOrder.OLDEST).map { it.id },
        )
        assertEquals(listOf(video.id, image.id), source.map { it.id })
    }
}
