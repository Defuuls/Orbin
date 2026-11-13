package com.orbin.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.orbin.uinext.tokens.OrbinType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** All provider board identities together, so missing or indistinguishable art is reviewable. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h900dp-mdpi")
class BoardIconScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allBoardIconsLight() = capture("board_icons_all_light", dark = false)

    @Test
    fun allBoardIconsDark() = capture("board_icons_all_dark", dark = true)

    private fun capture(
        name: String,
        dark: Boolean,
    ) {
        composeRule.setContent {
            NextTheme(darkTheme = dark, platform = NextPlatform.IOS) {
                Surface(modifier = Modifier.size(960.dp, 900.dp), color = next.background) {
                    Column(Modifier.padding(16.dp)) {
                        IconDirectory("fourchan", "4chan")
                        IconDirectory("bbwchan", "BBWChan")
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/androidHostTest/screenshots/$name.png")
    }
}

@Composable
private fun IconDirectory(
    provider: String,
    label: String,
) {
    val boards = registeredBoardIconIds.filter { it.startsWith("$provider/") }.sorted()
    Text(
        "$label · ${boards.size} boards",
        style = OrbinType.titleLarge,
        color = next.ink,
        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
    )
    boards.chunked(11).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { identity ->
                Column(
                    Modifier.width(80.dp).padding(bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BoardIcon(provider, identity.substringAfter('/'), modifier = Modifier.size(48.dp))
                    Text(
                        "/${identity.substringAfter('/')}/",
                        style = OrbinType.footnote.copy(fontSize = 11.sp, lineHeight = 14.sp),
                        color = next.ink,
                    )
                }
            }
        }
    }
}
