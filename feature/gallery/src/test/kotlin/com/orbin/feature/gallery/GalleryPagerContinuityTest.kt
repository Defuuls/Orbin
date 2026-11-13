package com.orbin.feature.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.MediaAttachment
import com.orbin.core.model.MediaType
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises production pager composition with a renderer that records player ownership. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class GalleryPagerContinuityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun expansionAndPrependsKeepTheSelectedPlayerMountedUntilTheUserSwipes() {
        val selected = attachment("selected")
        val before = attachment("before")
        val after = attachment("after")
        var content by mutableStateOf(
            GalleryContentState.Ready(
                media = persistentListOf(selected),
                threadMedia = persistentListOf(),
                threadMediaIndices = persistentListOf(-1),
                pageKeys = persistentListOf("selected-page"),
                isThreadResolved = false,
            ),
        )
        lateinit var selection: GalleryPagerSelection
        val mounts = mutableMapOf<String, Int>()
        val disposals = mutableMapOf<String, Int>()
        val activePlayers = mutableSetOf<String>()
        val reportedThreadIndices = mutableListOf<Int>()
        compose.setContent {
            val currentSelection = rememberGalleryPagerSelection(content, initialPage = 0)
            SideEffect { selection = currentSelection }
            GalleryMediaPager(
                content,
                currentSelection,
                Modifier.fillMaxSize().testTag("gallery-pager"),
            ) { item, active, _ ->
                if (active) {
                    DisposableEffect(Unit) {
                        mounts[item.id] = mounts.getOrDefault(item.id, 0) + 1
                        activePlayers += item.id
                        onDispose {
                            disposals[item.id] = disposals.getOrDefault(item.id, 0) + 1
                            activePlayers -= item.id
                        }
                    }
                    Box(Modifier.fillMaxSize().testTag("player:${item.id}"))
                } else {
                    Box(Modifier.fillMaxSize().testTag("poster:${item.id}"))
                }
            }
        }
        compose.onNodeWithTag("player:selected").assertIsDisplayed()
        compose.runOnIdle {
            content.threadMediaIndexAt(selection.activeIndexIn(content))?.let(reportedThreadIndices::add)
            assertThat(reportedThreadIndices).isEmpty()
            assertThat(mounts).containsExactly("selected", 1)
            assertThat(activePlayers).containsExactly("selected")
        }

        compose.runOnIdle { content = ready(listOf(before, selected, after)) }
        compose.onNodeWithTag("player:selected").assertIsDisplayed()
        compose.runOnIdle {
            assertThat(selection.state.settledPage).isEqualTo(1)
            content.threadMediaIndexAt(selection.activeIndexIn(content))?.let(reportedThreadIndices::add)
            assertThat(reportedThreadIndices).containsExactly(1)
            assertThat(mounts).containsExactly("selected", 1)
            assertThat(disposals).isEmpty()
            assertThat(activePlayers).containsExactly("selected")
        }

        compose.runOnIdle { content = ready(listOf(attachment("new-before"), before, selected, after)) }
        compose.onNodeWithTag("player:selected").assertIsDisplayed()
        compose.runOnIdle {
            assertThat(selection.state.settledPage).isEqualTo(2)
            content.threadMediaIndexAt(selection.activeIndexIn(content))?.let(reportedThreadIndices::add)
            assertThat(reportedThreadIndices).containsExactly(1, 2).inOrder()
            assertThat(mounts).containsExactly("selected", 1)
            assertThat(disposals).isEmpty()
            assertThat(activePlayers).containsExactly("selected")
        }

        compose.onNodeWithTag("gallery-pager").performTouchInput { swipeUp() }
        compose.onNodeWithTag("player:after").assertIsDisplayed()
        compose.runOnIdle {
            assertThat(selection.state.settledPage).isEqualTo(3)
            assertThat(activePlayers).containsExactly("after")
            assertThat(mounts).containsExactly("selected", 1, "after", 1)
            assertThat(disposals).containsExactly("selected", 1)
        }
    }

    @Test
    fun restorationKeepsTheReadersLaterPageWhileOnlyTheOriginalSeedIsAvailable() {
        val selected = attachment("selected")
        val after = attachment("after")
        val resolved = ready(listOf(selected, after))
        val provisional =
            GalleryContentState.Ready(
                media = persistentListOf(selected),
                threadMedia = persistentListOf(),
                threadMediaIndices = persistentListOf(-1),
                pageKeys = persistentListOf("selected-page"),
                isThreadResolved = false,
            )
        var content by mutableStateOf(resolved)
        lateinit var selection: GalleryPagerSelection
        val mounts = mutableMapOf<String, Int>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val currentSelection = rememberGalleryPagerSelection(content, initialPage = 0)
            SideEffect { selection = currentSelection }
            GalleryMediaPager(
                content,
                currentSelection,
                Modifier.fillMaxSize().testTag("gallery-pager"),
            ) { item, active, _ ->
                if (active) {
                    DisposableEffect(Unit) {
                        mounts[item.id] = mounts.getOrDefault(item.id, 0) + 1
                        onDispose { }
                    }
                    Box(Modifier.fillMaxSize().testTag("player:${item.id}"))
                } else {
                    Box(Modifier.fillMaxSize().testTag("poster:${item.id}"))
                }
            }
        }
        compose.onNodeWithTag("player:selected").assertIsDisplayed()
        compose.onNodeWithTag("gallery-pager").performTouchInput { swipeUp() }
        compose.onNodeWithTag("player:after").assertIsDisplayed()
        compose.runOnIdle { assertThat(selection.activePageKey).isEqualTo("page:after") }

        // A recreated ViewModel initially knows only route seed A; saved selection B must wait.
        compose.runOnIdle { content = provisional }
        compose.onNodeWithTag("player:selected").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("player:selected").assertDoesNotExist()
        compose.onNodeWithTag("player:after").assertDoesNotExist()
        compose.runOnIdle {
            assertThat(selection.activePageKey).isEqualTo("page:after")
            assertThat(mounts).containsExactly("selected", 1, "after", 1)
        }

        compose.runOnIdle { content = resolved }
        compose.onNodeWithTag("player:after").assertIsDisplayed()
        compose.runOnIdle {
            assertThat(selection.activePageKey).isEqualTo("page:after")
            assertThat(selection.state.settledPage).isEqualTo(1)
            assertThat(mounts).containsExactly("selected", 1, "after", 2)
        }
    }

    private fun ready(items: List<MediaAttachment>): GalleryContentState.Ready {
        val files = items.toImmutableList()
        return GalleryContentState.Ready(
            media = files,
            threadMedia = files,
            threadMediaIndices = files.indices.toImmutableList(),
            pageKeys = files.map { if (it.id == "selected") "selected-page" else "page:${it.id}" }.toImmutableList(),
        )
    }

    private fun attachment(id: String) =
        MediaAttachment(
            id = id,
            originalFileName = "$id.webm",
            extension = "webm",
            type = MediaType.VIDEO,
            sourceUrl = "https://media.example/$id.webm",
            thumbnailUrl = "https://media.example/$id.jpg",
        )
}
