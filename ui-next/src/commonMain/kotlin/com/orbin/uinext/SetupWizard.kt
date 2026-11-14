@file:Suppress("MagicNumber")

package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType

/** Theme choices exposed by the shared setup and update screens. */
enum class SetupThemeOption {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED,
}

/** Steps in the full-screen first-run / new-install setup flow. */
internal enum class OnboardingStep {
    WELCOME,
    APPEARANCE,
    PRIVACY,
    BOARDS,
}

/** Optional multi-step setup configuration for first-run / new-install flows. */
data class OnboardingSetupConfig(
    val theme: SetupThemeOption,
    val hideNsfwBoards: Boolean,
    val coverViolentMedia: Boolean,
    val onThemeChange: (SetupThemeOption) -> Unit,
    val onHideNsfwChange: (Boolean) -> Unit,
    val onCoverViolentMediaChange: (Boolean) -> Unit,
)

private val SplashGround = Color(0xFF0B1021)
private val SplashSurface = Color(0xFF141B33)
private val SplashBorder = Color(0xFF1E294A)
private val SplashMuted = Color(0xFF9FB0CE)
private val SplashCyan = Color(0xFF00D4FF)

@Composable
internal fun WelcomeSplashStep(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(SplashGround)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            OrbinLogoMark(modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                text = OrbinStyle.WORDMARK,
                style = OrbinType.largeTitle.withReaderFont().copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Privacy-focused, read-only imageboard browser",
                style = OrbinType.stateBody.withReaderFont(),
                color = SplashMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SplashHighlightRow("Encrypted local storage", "SQLCipher & Keystore on Android, Data Protection on iOS")
                SplashHighlightRow("HTTPS & DNS-over-HTTPS", "Every board, catalog, and media request stays encrypted")
                SplashHighlightRow("Zero telemetry", "No accounts, analytics, ads, or cloud backups")
            }
        }
        Spacer(Modifier.height(16.dp))
        OrbinFilledButton(
            label = "Get started",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SplashHighlightRow(
    title: String,
    subtitle: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(OrbinRadius.card))
                .background(SplashSurface)
                .border(1.dp, SplashBorder, RoundedCornerShape(OrbinRadius.card))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = OrbinType.rowTitle.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
        Text(
            text = subtitle,
            style = OrbinType.meta.withReaderFont(),
            color = SplashMuted,
        )
    }
}

@Composable
internal fun AppearanceSetupStep(
    theme: SetupThemeOption,
    onThemeChange: (SetupThemeOption) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val options =
        listOf(
            ThemeChoice(
                "System default",
                "Matches your device light or dark mode",
                SetupThemeOption.SYSTEM,
                Color(0xFF4338CA),
                SplashGround,
            ),
            ThemeChoice(
                "Light",
                "Crisp white ground with Electric Indigo accent",
                SetupThemeOption.LIGHT,
                Color.White,
                Color(0xFF4338CA),
            ),
            ThemeChoice(
                "Dark — Midnight Electric",
                "Midnight navy ground with Electric Cyan accent",
                SetupThemeOption.DARK,
                SplashGround,
                SplashCyan,
            ),
            ThemeChoice(
                "AMOLED — True Black",
                "Pure black & white for OLED displays",
                SetupThemeOption.AMOLED,
                Color.Black,
                Color.White,
            ),
        )
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(reader.background)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
    ) {
        SetupHeader(
            step = 2,
            totalSteps = 4,
            title = "Choose your theme",
            subtitle = "Midnight Electric across Light, Dark, and AMOLED.",
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = NextSpace.gutter, vertical = 8.dp)
                    .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEach { option ->
                val selected = theme == option.option
                ThemeOptionCard(
                    option = option,
                    selected = selected,
                    onClick = { onThemeChange(option.option) },
                )
            }
        }
        SetupBottomBar(
            backLabel = "Back",
            onBack = onBack,
            nextLabel = "Continue",
            onNext = onContinue,
        )
    }
}

private data class ThemeChoice(
    val title: String,
    val subtitle: String,
    val option: SetupThemeOption,
    val swatchBg: Color,
    val swatchAccent: Color,
)

@Composable
private fun ThemeOptionCard(
    option: ThemeChoice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OrbinCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (selected) {
                        Modifier.border(1.5.dp, reader.accent, RoundedCornerShape(OrbinRadius.card))
                    } else {
                        Modifier
                    },
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(option.swatchBg)
                        .border(1.dp, reader.hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(option.swatchAccent),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = option.title,
                    style = OrbinType.rowTitle.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
                    color = reader.ink,
                )
                Text(
                    text = option.subtitle,
                    style = OrbinType.meta.withReaderFont(),
                    color = reader.inkMuted,
                )
            }
            if (selected) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(reader.accent),
                )
            }
        }
    }
}

@Composable
internal fun PrivacySetupStep(
    hideNsfwBoards: Boolean,
    coverViolentMedia: Boolean,
    onHideNsfwChange: (Boolean) -> Unit,
    onCoverViolentMediaChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(reader.background)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
    ) {
        SetupHeader(
            step = 3,
            totalSteps = 4,
            title = "Privacy & content",
            subtitle = "Set your defaults — you can adjust these anytime in Settings.",
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = NextSpace.gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SetupToggleCard(
                title = "Hide adult (NSFW) boards",
                subtitle = "Keep 18+ boards hidden from directories and onboarding",
                checked = hideNsfwBoards,
                onCheckedChange = onHideNsfwChange,
            )
            SetupToggleCard(
                title = "Cover violent media",
                subtitle = "Blur thumbnails in posts flagged for violence until tapped",
                checked = coverViolentMedia,
                onCheckedChange = onCoverViolentMediaChange,
            )
            OrbinCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Private by design",
                        style = OrbinType.rowTitle.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
                        color = reader.ink,
                    )
                    Text(
                        text =
                            "All requests use HTTPS with DNS-over-HTTPS. " +
                                "Biometric app lock can be enabled in Settings.",
                        style = OrbinType.meta.withReaderFont(),
                        color = reader.inkMuted,
                    )
                }
            }
        }
        SetupBottomBar(
            backLabel = "Back",
            onBack = onBack,
            nextLabel = "Continue",
            onNext = onContinue,
        )
    }
}

@Composable
private fun SetupToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    OrbinCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = OrbinType.rowTitle.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
                    color = reader.ink,
                )
                Text(
                    text = subtitle,
                    style = OrbinType.meta.withReaderFont(),
                    color = reader.inkMuted,
                )
            }
            Box(Modifier.clearAndSetSemantics {}) {
                NextToggle(checked = checked, onCheckedChange = null)
            }
        }
    }
}

@Composable
internal fun SetupHeader(
    step: Int,
    totalSteps: Int,
    title: String,
    subtitle: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = NextSpace.gutter, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OrbinWordmark(logoSize = 32.dp)
        SetupStepIndicator(step = step, total = totalSteps)
        Text(
            text = title,
            style = OrbinType.stateTitle.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
            color = reader.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = subtitle,
            style = OrbinType.stateBody.withReaderFont(),
            color = reader.inkMuted,
        )
    }
}

@Composable
internal fun SetupStepIndicator(
    step: Int,
    total: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (index in 1..total) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index <= step) reader.accent else reader.hairline),
            )
        }
    }
}

@Composable
private fun SetupBottomBar(
    backLabel: String,
    onBack: () -> Unit,
    nextLabel: String,
    onNext: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(reader.background)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = NextSpace.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OrbinTextButton(label = backLabel, onClick = onBack)
        OrbinFilledButton(label = nextLabel, onClick = onNext)
    }
}
