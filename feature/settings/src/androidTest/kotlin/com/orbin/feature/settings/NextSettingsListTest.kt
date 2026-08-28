package com.orbin.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.AppSettings
import com.orbin.core.testing.repository.FakeDnsPrivacyMonitor
import com.orbin.core.testing.repository.FakeSettingsRepository
import com.orbin.uinext.NextTheme
import com.orbin.uinext.SettingKind
import com.orbin.uinext.SettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The settings list, wired exactly as the screen wires it.
 *
 * This replaces the per-category screen tests. What they were guarding is unchanged — a switch has
 * to reach the repository, quiet hours are gated on watch notifications, HTTPS-only reads as on
 * without being switchable, and the DNS notice tracks the monitor — but the surface those things
 * live on is now one list, so this exercises that instead of seven screens that no longer exist.
 */
@RunWith(AndroidJUnit4::class)
class NextSettingsListTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var repository: FakeSettingsRepository
    private val dnsMonitor = FakeDnsPrivacyMonitor()

    /** A row that does not reach the repository is decoration; this is the wiring under test. */
    @Test
    fun togglingARowWritesItThrough() {
        setContent()

        composeTestRule.onNodeWithText("Thread watch notifications").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertThat(repository.current.threadWatchNotificationsEnabled).isFalse()
    }

    /** Quiet hours only mean anything while watch notifications are on, so they are gated on it. */
    @Test
    fun quietHoursAppearOnlyWhileWatchNotificationsAreEnabled() {
        setContent()

        composeTestRule.onNodeWithText("Quiet hours start").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithText("Thread watch notifications").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Quiet hours start").assertDoesNotExist()
    }

    /** A string setting is edited under its own row and written on Save, not on every keystroke. */
    @Test
    fun editingATextRowWritesItThroughOnSave() {
        setContent()

        composeTestRule.onNodeWithText("Custom user agent").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Default").performTextReplacement("Orbin/1.0")
        assertThat(repository.current.userAgent).isEmpty()

        composeTestRule.onNodeWithText("Save").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertThat(repository.current.userAgent).isEqualTo("Orbin/1.0")
    }

    /** HTTPS-only is shown for transparency and must read as on without being switchable. */
    @Test
    fun httpsOnlyIsStatedRatherThanOffered() {
        setContent()

        composeTestRule.onNodeWithText("Always enforced").performScrollTo().assertIsDisplayed()
        assertThat(
            model()
                .groups
                .flatMap { it.second }
                .first { it.id == "httpsOnly" }
                .kind,
        ).isEqualTo(SettingKind.INFO)
    }

    /**
     * Encrypted DNS has no off switch, so this notice is the only way a user learns their lookups
     * have stopped being private. It has to actually change when the monitor says so.
     */
    @Test
    fun theDnsNoticeReflectsWhetherLookupsAreEncrypted() {
        setContent()

        composeTestRule
            .onNodeWithText("Encrypted DNS is always on", substring = true)
            .performScrollTo()
            .assertIsDisplayed()

        dnsMonitor.setFallbackActive(true)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("system resolver", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun model(fallback: Boolean = false) =
        buildSettings(repository.current, testSettingsViewModel(repository, dnsMonitor), "Up to date", fallback)

    /** The same wiring [NextSettingsScreen] does, minus the launchers, which need an activity. */
    private fun setContent(initial: AppSettings = AppSettings.Default) {
        repository = fakeSettingsRepository(initial)
        val viewModel = testSettingsViewModel(repository, dnsMonitor)
        composeTestRule.setContent {
            val settings by viewModel.settings.collectAsState()
            val fallback by viewModel.dnsFallbackActive.collectAsState()
            var expanded by remember { mutableStateOf<String?>(null) }
            val model = buildSettings(settings, viewModel, "Up to date", fallback)
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
