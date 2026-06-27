package com.orbin.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.orbin.core.designsystem.theme.OrbinPreviewTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests for the design system, captured with Roborazzi under Robolectric. Record golden
 * images with `./gradlew :core:designsystem:recordRoborazziDebug` and verify in CI with
 * `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ThemeScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightTheme() = capture(darkTheme = false, name = "theme_sample_light")

    @Test
    fun darkTheme() = capture(darkTheme = true, name = "theme_sample_dark")

    private fun capture(
        darkTheme: Boolean,
        name: String,
    ) {
        composeRule.setContent {
            OrbinPreviewTheme(darkTheme = darkTheme) {
                Surface { Sample() }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}

@Composable
private fun Sample() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Orbin", style = MaterialTheme.typography.headlineSmall)
        Card(modifier = Modifier.padding(top = 8.dp)) {
            Text("A themed card", modifier = Modifier.padding(16.dp))
        }
        Button(onClick = {}, modifier = Modifier.padding(top = 8.dp)) { Text("Action") }
    }
}
