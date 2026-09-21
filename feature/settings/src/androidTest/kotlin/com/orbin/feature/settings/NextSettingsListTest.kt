package com.orbin.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
 * Only the behaviour that needs one: a row has to reach the repository. Which rows are *present* —
 * the update check gated on the in-app updater, the three headings and nothing else — is a property
 * of the registry, so it is asserted in `SettingsIndexTest` where a row being off-screen cannot be
 * mistaken for a row being absent.
 *
 * The companion test for text rows went with the rows themselves: this list offers no text field
 * any more. `:ui-next` still renders [SettingKind.TEXT] and the registry still carries the commit
 * plumbing, so that test comes back with the first text row that does.
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

        scrollTo("Hide NSFW boards").performClick()
        composeTestRule.waitForIdle()

        assertThat(repository.current.hideNsfwBoards).isTrue()
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
            val model = buildSettings(settings, viewModel, "Up to date")
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
