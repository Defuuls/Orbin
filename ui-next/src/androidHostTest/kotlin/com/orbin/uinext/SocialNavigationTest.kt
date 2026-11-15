package com.orbin.uinext

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import com.orbin.uinext.tokens.ReaderSize
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The bottom bar is the only primary navigation: Boards, Feed, Library and Settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SocialNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the bar offers the four primary destinations in order and dispatches each once`() {
        val selected = mutableListOf<NextDestination>()
        composeRule.setContent {
            NextTheme { OrbinBottomBar(selected = NextDestination.FEED, onSelect = selected::add) }
        }

        val primary =
            listOf(NextDestination.BOARDS, NextDestination.FEED, NextDestination.LIBRARY, NextDestination.SETTINGS)
        primary.zip(listOf("Boards", "Feed", "Library", "Settings")).forEach { (destination, label) ->
            composeRule
                .onNodeWithTag(OrbinNavigationTags.ENTRY + destination.name)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(label)))
                .assertHeightIsAtLeast(ReaderSize.touchTarget)
                .performClick()
        }
        composeRule.onNodeWithTag(OrbinNavigationTags.ENTRY + NextDestination.FEED.name).assertIsSelected()
        // Reselecting the visible tab is a no-op.
        composeRule.runOnIdle {
            assertThat(selected)
                .containsExactly(NextDestination.BOARDS, NextDestination.LIBRARY, NextDestination.SETTINGS)
                .inOrder()
        }
    }

    @Test
    fun `any Library section selects the Library tab and its badge counts watched replies`() {
        composeRule.setContent {
            NextTheme { OrbinBottomBar(selected = NextDestination.WATCHED, onSelect = {}, watchedBadge = 3) }
        }

        composeRule
            .onNodeWithTag(OrbinNavigationTags.ENTRY + NextDestination.LIBRARY.name)
            .assertIsSelected()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3 new replies on watched threads"),
            )
        composeRule.onNodeWithTag(OrbinNavigationTags.ENTRY + NextDestination.FEED.name).assertIsNotSelected()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `labels show at the default text size`() {
        composeRule.setContent {
            NextTheme { OrbinBottomBar(selected = NextDestination.FEED, onSelect = {}) }
        }

        composeRule.onAllNodesWithTag(OrbinNavigationTags.LABEL, useUnmergedTree = true).assertCountEquals(4)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `at large text every tab goes icon-only together and keeps its name`() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2.5f)) {
                NextTheme { OrbinBottomBar(selected = NextDestination.FEED, onSelect = {}) }
            }
        }

        composeRule.onAllNodesWithTag(OrbinNavigationTags.LABEL, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Library").assertHeightIsAtLeast(ReaderSize.touchTarget)
        composeRule.onNodeWithContentDescription("Settings").assertHeightIsAtLeast(ReaderSize.touchTarget)
    }

    @Test
    fun `a Library section is titled Library with section tabs and the primary bar`() {
        val sections = mutableListOf<LibrarySection>()
        val primary = mutableListOf<NextDestination>()
        var backs = 0
        composeRule.setContent {
            NextTheme {
                CompositionLocalProvider(
                    LocalLibraryNavigation provides
                        LibraryNavigation(onSelect = sections::add, onPrimary = primary::add, watchedBadge = 2),
                ) {
                    ReaderScaffold(
                        "Saved",
                        destination = NextDestination.SAVED,
                        onDestination = {},
                        onBack = { backs++ },
                    ) {}
                }
            }
        }

        composeRule.onNodeWithTag(ReaderAppBarTags.TITLE, useUnmergedTree = true).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.Text,
                listOf(
                    androidx.compose.ui.text
                        .AnnotatedString("Library"),
                ),
            ),
        )
        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
        composeRule.onNodeWithTag(LibraryTags.SECTION + LibrarySection.SAVED.name).assertIsSelected()
        composeRule
            .onNodeWithTag(LibraryTags.SECTION + LibrarySection.WATCHED.name)
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2 new replies on watched threads"),
            ).performClick()
        composeRule.onNodeWithTag(OrbinNavigationTags.ENTRY + NextDestination.LIBRARY.name).assertIsSelected()
        composeRule.onNodeWithTag(OrbinNavigationTags.ENTRY + NextDestination.FEED.name).performClick()
        composeRule.runOnIdle {
            assertThat(sections).containsExactly(LibrarySection.WATCHED)
            assertThat(primary).containsExactly(NextDestination.FEED)
            assertThat(backs).isEqualTo(0)
        }
    }

    @Test
    fun `secondary routes show Back and no Menu`() {
        var backs = 0
        composeRule.setContent {
            NextTheme {
                ReaderScaffold(
                    "Settings",
                    destination = NextDestination.SETTINGS,
                    onDestination = {},
                    onBack = { backs++ },
                ) {}
            }
        }

        composeRule.onNodeWithContentDescription("Menu").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.runOnIdle { assertThat(backs).isEqualTo(1) }
    }

    @Test
    fun `the app bar keeps two actions including Search and moves the rest into More`() {
        val refresh = ReaderAction(ReaderIcons.Refresh, "Refresh") {}
        val saved = ReaderAction(ReaderIcons.Saved, "Saved") {}
        val search = ReaderAction(ReaderIcons.Search, "Search") {}

        assertThat(appBarVisibleActions(listOf(refresh, search))).containsExactly(refresh, search).inOrder()
        assertThat(appBarVisibleActions(listOf(refresh, saved, search))).containsExactly(refresh, search).inOrder()
        assertThat(appBarVisibleActions(listOf(refresh, saved, search), budget = 1)).containsExactly(search)
    }

    @Test
    fun `a third app bar action moves into More`() {
        composeRule.setContent {
            NextTheme {
                ReaderAppBar(
                    title = "Feed",
                    actions =
                        listOf(
                            ReaderAction(ReaderIcons.Refresh, "Refresh") {},
                            ReaderAction(ReaderIcons.Saved, "Saved") {},
                            ReaderAction(ReaderIcons.Search, "Search") {},
                        ),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Search").assertHeightIsAtLeast(ReaderSize.touchTarget)
        composeRule.onNodeWithContentDescription("Refresh").assertHeightIsAtLeast(ReaderSize.touchTarget)
        composeRule.onNodeWithContentDescription("Saved").assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderAppBarTags.OVERFLOW).performClick()
        composeRule.onNodeWithText("Saved").assertHeightIsAtLeast(ReaderSize.touchTarget)
    }
}
