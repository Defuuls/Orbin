package com.orbin.uinext

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class CollectionActionsTest {
    @get:Rule val compose = createComposeRule()
    private val row =
        ThreadCollectionRow("site/g/7", "Stored thread", "Site · /g/", availability = ThreadCollectionStatus.ARCHIVED)

    @Test
    fun clearRequiresConfirmationAndCancelKeepsRecords() {
        var clears = 0
        var orderChanges = 0
        compose.setContent {
            NextTheme {
                ThreadCollectionScreen(
                    "Saved",
                    listOf(row),
                    {},
                    onToggleOrder = { orderChanges++ },
                    onClear = { clears++ },
                )
            }
        }
        compose.onNodeWithText("Archived", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Manage threads").performClick()
        compose.onNodeWithText("Oldest first").performClick()
        compose.runOnIdle { assertEquals(1, orderChanges) }
        compose.onNodeWithText("Manage threads").performClick()
        compose.onNodeWithText("Clear this collection").performClick()
        compose.runOnIdle { assertEquals(0, clears) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, clears) }
        compose.onNodeWithText("Manage threads").performClick()
        compose.onNodeWithText("Clear this collection").performClick()
        compose.onNodeWithText("Clear this collection").performClick()
        compose.runOnIdle { assertEquals(1, clears) }
    }

    @Test
    fun recordKeepsOpenActionAndLongPressOffersActualRemoveAndMarkRead() {
        var opens = 0
        var removed: ThreadCollectionRow? = null
        var read: ThreadCollectionRow? = null
        compose.setContent {
            NextTheme {
                ThreadCollectionScreen(
                    "Saved",
                    listOf(row),
                    { opens++ },
                    onRemove = { removed = it },
                    onMarkRead = { read = it },
                )
            }
        }
        val record = compose.onNodeWithTag(ReaderSavedTags.row(row.id))
        record.performClick()
        compose.runOnIdle { assertEquals(1, opens) }
        record.performTouchInput { longClick() }
        compose.onNodeWithText("Mark as read").performClick()
        compose.runOnIdle {
            assertEquals(row, read)
            assertEquals(null, removed)
        }
        record.performTouchInput { longClick() }
        compose.onNodeWithText("Remove", substring = false).performClick()
        compose.runOnIdle {
            assertEquals(row, removed)
            assertEquals(1, opens)
        }
    }
}
