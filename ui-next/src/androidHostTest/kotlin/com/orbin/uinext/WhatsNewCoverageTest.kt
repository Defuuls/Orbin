package com.orbin.uinext

import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Native media must remain dormant while release notes are pending or cover the reader. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WhatsNewCoverageTest {
    @get:Rule
    val compose = createComposeRule()

    private val notes = ReleaseNotes("197-Test", listOf(ReleaseNoteSection("Fixed", listOf("Board feeds"))))

    @Test
    fun pendingAndVisibleNotesCoverContentThenDismissalResumesBeforePersistenceFinishes() {
        val seen = MutableSharedFlow<String?>(replay = 1)
        val persisted = CompletableDeferred<Unit>()
        var covered: Boolean? = null
        var saveStarted = false
        var saveFinished = false
        var savedVersion: String? = null
        compose.setContent {
            NextTheme {
                WhatsNewOnUpdate(
                    lastSeen = seen,
                    onSeen = {
                        saveStarted = true
                        savedVersion = it
                        persisted.await()
                        saveFinished = true
                    },
                    visible = true,
                    notes = notes,
                ) { current ->
                    SideEffect { covered = current }
                    Text("Reader content")
                }
            }
        }
        compose.runOnIdle { assertEquals(true, covered) }
        compose.onNodeWithText("Reader content").assertExists()
        compose.onNodeWithText("Done").assertDoesNotExist()

        compose.runOnIdle { assertTrue(seen.tryEmit("196-Previous")) }
        compose.onNodeWithText("Done").assertExists()
        compose.runOnIdle { assertEquals(true, covered) }
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle {
            assertEquals(false, covered)
            assertTrue(saveStarted)
            assertFalse(saveFinished)
            assertEquals(notes.version, savedVersion)
        }
        compose.onNodeWithText("Done").assertDoesNotExist()
        compose.runOnIdle { persisted.complete(Unit) }
        compose.waitUntil { saveFinished }
        compose.runOnIdle { assertEquals(false, covered) }
    }

    @Test
    fun savedCurrentReleaseLeavesContentUncovered() {
        val seen = MutableStateFlow(notes.version)
        var covered: Boolean? = null
        compose.setContent {
            NextTheme {
                WhatsNewOnUpdate(seen, {}, visible = true, notes = notes) { current ->
                    SideEffect { covered = current }
                    Text("Reader content")
                }
            }
        }
        compose.runOnIdle { assertEquals(false, covered) }
        compose.onNodeWithText("Reader content").assertExists()
        compose.onNodeWithText("Done").assertDoesNotExist()
    }

    @Test
    fun hiddenNotesNeverCoverContentEvenWhileStoredVersionIsPending() {
        val seen = MutableSharedFlow<String?>(replay = 1)
        var covered: Boolean? = null
        compose.setContent {
            NextTheme {
                WhatsNewOnUpdate(seen, {}, visible = false, notes = notes) { current ->
                    SideEffect { covered = current }
                    Text("Reader content")
                }
            }
        }
        compose.runOnIdle { assertEquals(false, covered) }
        compose.runOnIdle { assertTrue(seen.tryEmit(null)) }
        compose.runOnIdle { assertEquals(false, covered) }
        compose.onNodeWithText("Done").assertDoesNotExist()
    }

    @Test
    fun visibilityChangesRemoveTheCoverAndRestoreItOnlyWhenNotesCanShow() {
        val seen = MutableStateFlow<String?>("196-Previous")
        var visible by mutableStateOf(true)
        var covered: Boolean? = null
        compose.setContent {
            NextTheme {
                WhatsNewOnUpdate(seen, {}, visible = visible, notes = notes) { current ->
                    SideEffect { covered = current }
                    Text("Reader content")
                }
            }
        }
        compose.onNodeWithText("Done").assertExists()
        compose.runOnIdle {
            assertEquals(true, covered)
            visible = false
        }
        compose.onNodeWithText("Done").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(false, covered)
            visible = true
        }
        compose.onNodeWithText("Done").assertExists()
        compose.runOnIdle { assertEquals(true, covered) }
    }
}
