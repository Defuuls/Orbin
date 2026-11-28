package com.orbin.ios

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import com.orbin.core.model.BoardId
import com.orbin.core.model.ProviderId
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.uinext.components.NextTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-xhdpi")
class SourceReaderFrameTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val thread = Route.ThreadPage(ThreadKey(ProviderId("source"), BoardId("g"), ThreadId(42)))

    @Test
    fun queryAndSourceScrollSurviveSelectionResizeAndReturn() {
        var width by mutableStateOf(960.dp)
        var route by mutableStateOf(SourceReaderRoute(Route.Search))
        lateinit var sourceState: LazyListState
        composeRule.setContent {
            NextTheme {
                val holder = rememberSaveableStateHolder()
                Box(Modifier.size(width, 700.dp)) {
                    SourceReaderFrame(route, holder, sourceContent = { _, _ ->
                        var query by rememberSaveable { mutableStateOf("") }
                        val list = rememberLazyListState()
                        SideEffect { sourceState = list }
                        Column(Modifier.fillMaxSize()) {
                            Text("Query: $query", Modifier.testTag("query").clickable { query = "trees" })
                            LazyColumn(Modifier.weight(1f).testTag("source-list"), state = list) {
                                items(100) { Text("Source row $it", Modifier.height(48.dp)) }
                            }
                        }
                    }, threadContent = {
                        var files by rememberSaveable { mutableStateOf(false) }
                        Text(if (files) "Files" else "Posts", Modifier.testTag("thread").clickable { files = !files })
                    })
                }
            }
        }
        composeRule.onNodeWithTag("query").performClick()
        composeRule.onNodeWithTag("source-list").performScrollToIndex(20)
        composeRule.runOnIdle { route = SourceReaderRoute(Route.Search, thread) }
        composeRule.onNodeWithText("Query: trees").assertIsDisplayed()
        composeRule.onNodeWithTag("thread").performClick()
        composeRule.runOnIdle { width = 600.dp }
        composeRule.onNodeWithTag("source-list").assertDoesNotExist()
        composeRule.onNodeWithText("Files").assertIsDisplayed()
        composeRule.runOnIdle { width = 960.dp }
        composeRule.onNodeWithText("Query: trees").assertIsDisplayed()
        composeRule.onNodeWithText("Files").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(20, sourceState.firstVisibleItemIndex)
            route =
                SourceReaderRoute(Route.Search)
        }
        composeRule.onNodeWithText("Select a thread").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(20, sourceState.firstVisibleItemIndex) }
    }
}
