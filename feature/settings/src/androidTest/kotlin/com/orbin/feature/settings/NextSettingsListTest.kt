package com.orbin.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.AppSettings
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.uinext.NextTheme
import com.orbin.uinext.SettingKind
import com.orbin.uinext.SettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Editing a setting from the list, on a device.
 *
 * Only the two behaviours that need one: a row has to reach the repository, and a text row has to
 * write on Save rather than on every keystroke. Which rows are *present* — quiet hours gated on
 * watch notifications, HTTPS-only stated rather than offered, the DNS notice tracking the monitor —
 * is a property of the registry, so it is asserted in `SettingsIndexTest` where a row being
 * off-screen cannot be mistaken for a row being absent.
 *
 * The list is lazy, so a row has to be scrolled to before it exists to look at.
 */
@RunWith(AndroidJUnit4::class)
class NextSettingsListTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var repository: FakeSettingsRepository

    /** A row that does not reach the repository is decoration; this is the wiring under test. */
    @Test
    fun togglingARowWritesItThrough() {
        setContent()

        scrollTo("Thread watch notifications").performClick()
        composeTestRule.waitForIdle()

        assertThat(repository.current.threadWatchNotificationsEnabled).isFalse()
    }

    /** A string setting is written on Save, not on every keystroke. */
    @Test
    fun editingATextRowWritesItThroughOnSave() {
        setContent()

        scrollTo("Custom user agent").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("Orbin/1.0")
        composeTestRule.waitForIdle()
        assertThat(repository.current.userAgent).isEmpty()

        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasTextExactly("Save"))
        composeTestRule.onNode(hasTextExactly("Save")).performClick()
        composeTestRule.waitForIdle()

        assertThat(repository.current.userAgent).isEqualTo("Orbin/1.0")
    }

    /** Brings a row into composition — in a lazy list an off-screen row is not there to be found. */
    private fun scrollTo(label: String) =
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText(label))
            .run { composeTestRule.onNodeWithText(label) }

    /** The same wiring [NextSettingsScreen] does, minus the launchers, which need an activity. */
    private fun setContent(initial: AppSettings = AppSettings.Default) {
        repository = fakeSettingsRepository(initial)
        val viewModel = testSettingsViewModel(repository)
        composeTestRule.setContent {
            val settings by viewModel.settings.collectAsState()
            var expanded by remember { mutableStateOf<String?>(null) }
            val model = buildSettings(settings, viewModel, "Up to date", dnsFallbackActive = false)
            NextTheme {
                SettingsScreen(
                    groups = model.groups,
                    expandedId = expanded,
                    onActivate = { item ->
                        when (item.kind) {
                            SettingKind.TOGGLE -> model.toggle(item.id)
                            SettingKind.CHOICE, SettingKind.TEXT ->
                                expanded = if (expanded == item.id) null else item.id
                            SettingKind.ACTION, SettingKind.INFO -> Unit
                        }
                    },
                    onSelectOption = { item, index ->
                        model.choose(item.id, index)
                        expanded = null
                    },
                    onCommitText = { item, value ->
                        model.commit(item.id, value)
                        expanded = null
                    },
                )
            }
        }
    }
}
