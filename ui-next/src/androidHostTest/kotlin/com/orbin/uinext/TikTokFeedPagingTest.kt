package com.orbin.uinext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Paging is also a native-player ownership contract, including source switches and cancelled drags. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
class TikTokFeedPagingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `only the settled source-qualified page owns active media`() {
        var rows by mutableStateOf(listOf(row("fourchan"), row("bbwchan")))
        val media = mutableMapOf<String, Boolean>()
        val owners = mutableListOf<String?>()
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    rows = rows,
                    onActivePreviewChanged = { owners += it },
                    mediaContent = { row, modifier, active ->
                        DisposableEffect(row.id) { onDispose { media.remove(row.id) } }
                        SideEffect { media[row.id] = active }
                        Box(modifier)
                    },
                )
            }
        }
        composeRule.runOnIdle { assertEquals(setOf("fourchan/g/7"), media.filterValues { it }.keys) }
        composeRule.onNodeWithTag(TikTokFeedTags.PAGER).performScrollToIndex(1)
        composeRule.runOnIdle {
            assertEquals(setOf("bbwchan/g/7"), media.filterValues { it }.keys)
            assertEquals("bbwchan/g/7", owners.last())
            rows = listOf(row("fourchan"))
        }
        composeRule.waitForIdle()
        composeRule.waitUntil { owners.lastOrNull() == "fourchan/g/7" }
        composeRule.runOnIdle { assertEquals(setOf("fourchan/g/7"), media.filterValues { it }.keys) }
    }

    @Test
    fun `reordering threads retains the displayed owner without activating its neighbour`() {
        val first = row("fourchan")
        val second = row("bbwchan")
        var rows by mutableStateOf(listOf(first, second))
        val owners = mutableListOf<String?>()
        val media = mutableMapOf<String, Boolean>()
        val renderedStates = mutableListOf<Pair<String, Boolean>>()
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    rows = rows,
                    onActivePreviewChanged = { owners += it },
                    mediaContent = { row, modifier, active ->
                        DisposableEffect(row.id) { onDispose { media.remove(row.id) } }
                        SideEffect {
                            media[row.id] = active
                            renderedStates += row.id to active
                        }
                        Box(modifier)
                    },
                )
            }
        }
        composeRule.runOnIdle {
            assertEquals(first.id, owners.last())
            assertEquals(setOf(first.id), media.filterValues { it }.keys)
            owners.clear()
            renderedStates.clear()
            rows = listOf(second, first)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TikTokFeedTags.page(first.id)).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(setOf(first.id), media.filterValues { it }.keys)
            assertTrue(
                "Relocation must retain the owner without a null handoff: $owners",
                owners.all { it == first.id },
            )
            assertTrue(
                "The retained player must remain active: $renderedStates",
                renderedStates.filter { it.first == first.id }.all { it.second },
            )
            assertTrue(
                "The neighbour must never start during relocation: $renderedStates",
                renderedStates.none {
                    it.first ==
                        second.id &&
                        it.second
                },
            )
        }
        composeRule.onNodeWithTag(TikTokFeedTags.PAGER).performScrollToIndex(0)
        composeRule.onNodeWithTag(TikTokFeedTags.page(second.id)).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(second.id, owners.last())
            assertEquals(setOf(second.id), media.filterValues { it }.keys)
        }
    }

    @Test
    fun `removing Home releases its active preview ownership`() {
        var visible by mutableStateOf(true)
        val owners = mutableListOf<String?>()
        composeRule.setContent {
            NextTheme {
                if (visible) {
                    FeedScreen(rows = listOf(row("fourchan")), onActivePreviewChanged = { owners += it })
                } else {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        composeRule.runOnIdle {
            assertEquals("fourchan/g/7", owners.last())
            visible = false
        }
        composeRule.runOnIdle { assertEquals(null, owners.last()) }
    }

    @Test
    fun `Following uses the supplied board feed without requiring a watched thread`() {
        val owners = mutableListOf<String?>()
        var following by mutableStateOf(false)
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    rows = if (following) listOf(row("fourchan")) else listOf(row("bbwchan").copy(watched = true)),
                    following = following,
                    onFollowingChange = { following = it },
                    onActivePreviewChanged = { owners += it },
                )
            }
        }
        composeRule.onNodeWithTag(TikTokFeedTags.FOLLOWING).performClick()
        composeRule.onNodeWithTag(TikTokFeedTags.FOLLOWING).assertIsSelected()
        composeRule.runOnIdle { assertEquals("fourchan/g/7", owners.last()) }
    }

    @Test
    fun `tabs remain usable while loading and after a source error`() {
        var following by mutableStateOf(false)
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    rows = emptyList(),
                    following = following,
                    onFollowingChange = { following = it },
                    isLoading = !following,
                    errorMessage = if (following) "The source is temporarily unavailable" else null,
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("Loading videos…").assertIsDisplayed()
        composeRule.onNodeWithTag(TikTokFeedTags.FOLLOWING).performClick().assertIsSelected()
        composeRule.onNodeWithText("The source is temporarily unavailable").assertIsDisplayed()
        composeRule.onNodeWithTag(TikTokFeedTags.FOR_YOU).performClick().assertIsSelected()
        composeRule.onNodeWithText("Loading videos…").assertIsDisplayed()
    }

    @Test
    fun `returning to For You restores its page and releases the other tab owner`() {
        var following by mutableStateOf(false)
        val discovery = listOf(row("fourchan"), row("bbwchan"), row("third"))
        val owners = mutableListOf<String?>()
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    rows = if (following) listOf(row("followed")) else discovery,
                    following = following,
                    onFollowingChange = { following = it },
                    onActivePreviewChanged = { owners += it },
                )
            }
        }
        composeRule.onNodeWithTag(TikTokFeedTags.PAGER).performScrollToIndex(2)
        composeRule.runOnIdle { assertEquals("third/g/7", owners.last()) }
        composeRule.onNodeWithTag(TikTokFeedTags.FOLLOWING).performClick()
        composeRule.runOnIdle { assertEquals("followed/g/7", owners.last()) }
        composeRule.onNodeWithTag(TikTokFeedTags.FOR_YOU).performClick()
        composeRule.runOnIdle { assertEquals("third/g/7", owners.last()) }
    }

    @Test
    fun `completed cached batches request more even when their video row count does not change`() {
        var completedBatch by mutableStateOf(8)
        var calls = 0
        val first = row("first")
        val second = row("second")
        val later = row("later")
        composeRule.setContent {
            NextTheme {
                FeedScreen(
                    rows = if (completedBatch >= 24) listOf(first, second, later) else listOf(first, second),
                    loadMoreKey = completedBatch,
                    onLoadMore =
                        if (completedBatch >= 24) {
                            null
                        } else {
                            {
                                calls++
                                // The middle batch contains only cached images: no row-count or busy-state change.
                                completedBatch += 8
                            }
                        },
                )
            }
        }
        composeRule.waitUntil { calls == 2 }
        composeRule.onNodeWithTag(TikTokFeedTags.PAGER).performScrollToIndex(2)
        composeRule.onNodeWithTag(TikTokFeedTags.page(later.id)).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(24, completedBatch) }
    }

    @Test
    fun `a cancelled drag retains the current player owner`() {
        val owners = mutableListOf<String?>()
        composeRule.setContent {
            NextTheme {
                FeedScreen(rows = listOf(row("fourchan"), row("bbwchan")), onActivePreviewChanged = { owners += it })
            }
        }
        composeRule.onNodeWithTag(TikTokFeedTags.PAGER).performTouchInput {
            down(center)
            moveTo(center + Offset(0f, -80f), delayMillis = 600)
            advanceEventTime(600)
            up()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals("fourchan/g/7", owners.last())
            assertTrue(owners.dropWhile { it != "fourchan/g/7" }.none { it == null || it == "bbwchan/g/7" })
        }
    }

    private fun row(provider: String) =
        FeedRow(
            id = "$provider/g/7",
            subject = "Same thread number",
            board = "/g/",
            site = if (provider == "fourchan") "4chan" else "BBWChan",
            activity = "1m",
            replies = 12,
            media = 1,
            mediaLabel = "MP4",
        )
}
