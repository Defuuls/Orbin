package com.orbin.app.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.orbin.uinext.components.NextDestination
import com.orbin.uinext.components.NextTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w1280dp-h800dp-xhdpi")
class SourceReaderDestinationTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sourceQueryAndScrollRemainWhenSelectingReplacingAndClosingThreads() {
        composeRule.setContent {
            NextTheme {
                Box(Modifier.size(960.dp, 700.dp)) {
                    SourceReaderDestination(
                        twoPane = true,
                        title = "Search",
                        destination = NextDestination.SEARCH,
                        onOpenThread = { _, _, _, _ -> error("Wide selection must stay in its source") },
                        onOpenGallery = { _, _, _, _ -> },
                        sourceContent = { open ->
                            var query by rememberSaveable { mutableStateOf("") }
                            val list = rememberLazyListState()
                            Column(Modifier.fillMaxSize()) {
                                Text("Query: $query", Modifier.testTag("query").clickable { query = "trees" })
                                LazyColumn(Modifier.weight(1f).testTag("source"), state = list) {
                                    items(100) { index ->
                                        Text(
                                            "Result $index",
                                            Modifier.height(48.dp).clickable {
                                                open("source", "g", index.toLong(), "Thread $index")
                                            },
                                        )
                                    }
                                }
                            }
                        },
                        threadContent = {
                            route,
                            close,
                            ->
                            Text("Thread ${route.thread}", Modifier.testTag("thread").clickable(onClick = close))
                        },
                    )
                }
            }
        }
        composeRule.onNodeWithTag("query").performClick()
        composeRule.onNodeWithTag("source").performScrollToIndex(20)
        composeRule.onNodeWithText("Result 20").performClick()
        composeRule.onNodeWithText("Thread 20").assertIsDisplayed()
        composeRule.onNodeWithText("Query: trees").assertIsDisplayed()
        composeRule.onNodeWithText("Result 21").performClick()
        composeRule.onNodeWithText("Thread 21").assertIsDisplayed()
        composeRule.onNodeWithTag("thread").performClick()
        composeRule.onNodeWithText("Select a thread").assertIsDisplayed()
        composeRule.onNodeWithText("Result 20").assertIsDisplayed()
        composeRule.onNodeWithText("Query: trees").assertIsDisplayed()
    }

    @Test
    fun shrinkingPromotesTheSelectedThreadExactlyOnceAndKeepsItsSource() {
        var wide by mutableStateOf(true)
        val promoted = mutableListOf<Route.Thread>()
        composeRule.setContent {
            NextTheme {
                Box(Modifier.size(if (wide) 960.dp else 700.dp, 700.dp)) {
                    SourceReaderDestination(
                        twoPane = wide,
                        title = "History",
                        destination = null,
                        onOpenThread = { provider, board, thread, title ->
                            promoted +=
                                Route.Thread(provider, board, thread, title)
                        },
                        onOpenGallery = { _, _, _, _ -> },
                        sourceContent = { open ->
                            Text("Visit", Modifier.testTag("visit").clickable { open("source", "g", 42, "Topic") })
                        },
                        threadContent = { route, _ -> Text("Thread ${route.thread}") },
                    )
                }
            }
        }
        composeRule.onNodeWithTag("visit").performClick()
        composeRule.onNodeWithText("Thread 42").assertIsDisplayed()
        composeRule.runOnIdle { wide = false }
        composeRule.onNodeWithTag("visit").assertIsDisplayed()
        composeRule.runOnIdle { assertThat(promoted).containsExactly(Route.Thread("source", "g", 42, "Topic")) }
        composeRule.runOnIdle { wide = true }
        composeRule.onNodeWithText("Select a thread").assertIsDisplayed()
        composeRule.onNodeWithText("Thread 42").assertDoesNotExist()
        composeRule.runOnIdle { assertThat(promoted).hasSize(1) }
    }
}
