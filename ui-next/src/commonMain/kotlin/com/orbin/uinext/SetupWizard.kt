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
import androidx.compose.ui.unit.sp
import com.orbin.uinext.tokens.NextSpace
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderAmoledColors
import com.orbin.uinext.tokens.ReaderDarkColors
import com.orbin.uinext.tokens.ReaderLightColors

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

private val SplashGround: Color
    @Composable get() = reader.background
private val SplashSurface: Color
    @Composable get() = reader.surfaceVariant
private val SplashBorder: Color
    @Composable get() = reader.hairline
private val SplashMuted: Color
    @Composable get() = reader.inkMuted
private val SplashAccent: Color
    @Composable get() = reader.accent

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
            OrbinLogoMark(modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                text = OrbinStyle.WORDMARK,
                style =
                    OrbinType.largeTitle.withReaderFont().copy(
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Black,
                    ),
                color = reader.ink,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Privacy-focused, read-only imageboard browser",
                style = OrbinType.stateBody.withReaderFont(),
                color = SplashMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(OrbinRadius.pill))
                        .background(SplashSurface)
                        .border(1.dp, SplashAccent.copy(alpha = 0.45f), RoundedCornerShape(OrbinRadius.pill))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "READ-ONLY · NO ACCOUNTS",
                    style = OrbinType.caption2.withReaderFont().copy(fontWeight = FontWeight.Bold),
                    color = SplashAccent,
                )
            }
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SplashHighlightRow(
                    icon = ReaderIcons.Lock,
                    title = "Encrypted local storage",
                    subtitle = "SQLCipher & Keystore on Android, Data Protection on iOS",
                )
                SplashHighlightRow(
                    icon = ReaderIcons.Shield,
                    title = "HTTPS & DNS-over-HTTPS",
                    subtitle = "Every board, catalog, and media request stays encrypted",
                )
                SplashHighlightRow(
                    icon = ReaderIcons.Hide,
                    title = "Zero telemetry",
                    subtitle = "No accounts, analytics, ads, or cloud backups",
                )
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(OrbinRadius.card))
                .background(SplashSurface)
                .border(1.dp, SplashBorder, RoundedCornerShape(OrbinRadius.card))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SplashAccent,
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(SplashAccent.copy(alpha = 0.16f))
                    .padding(8.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = OrbinType.rowTitle.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
                color = reader.ink,
            )
            Text(
                text = subtitle,
                style = OrbinType.meta.withReaderFont(),
                color = SplashMuted,
            )
        }
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
                reader.background,
                reader.accent,
            ),
            ThemeChoice(
                "Light",
                "Soft light surfaces with an orange accent",
                SetupThemeOption.LIGHT,
                Color.White,
                ReaderLightColors.accent,
            ),
            ThemeChoice(
                "Dark",
                "Quiet dark surfaces with an orange accent",
                SetupThemeOption.DARK,
                ReaderDarkColors.background,
                ReaderDarkColors.accent,
            ),
            ThemeChoice(
                "AMOLED — True Black",
                "Pure black surfaces with an orange accent",
                SetupThemeOption.AMOLED,
                ReaderAmoledColors.background,
                ReaderAmoledColors.accent,
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
            // Chance-inspired SavedThemeThumbnail mini preview card (simulated header + post + greentext)
            Column(
                modifier =
                    Modifier
                        .size(width = 48.dp, height = 38.dp)
                        .clip(RoundedCornerShape(OrbinRadius.control))
                        .background(option.swatchBg)
                        .border(1.dp, reader.hairline, RoundedCornerShape(OrbinRadius.control)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(option.swatchAccent.copy(alpha = 0.28f)),
                )
                Column(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(width = 22.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(option.swatchAccent),
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(width = 30.dp, height = 3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF789922)),
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(width = 26.dp, height = 3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(option.swatchAccent.copy(alpha = 0.55f)),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = option.title,
                    style = OrbinType.rowTitle.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) reader.accent else reader.ink,
                )
                Text(
                    text = option.subtitle,
                    style = OrbinType.meta.withReaderFont(),
                    color = reader.inkMuted,
                )
            }
            ReaderSettingsChoiceMark(selected = selected)
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
                ReaderSettingsSwitch(checked)
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
    Column(Modifier.fillMaxWidth().background(reader.background)) {
        ReaderHairline()
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(horizontal = NextSpace.gutter, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OrbinTextButton(label = backLabel, onClick = onBack, modifier = Modifier.weight(1f))
            OrbinFilledButton(label = nextLabel, onClick = onNext, modifier = Modifier.weight(1f))
        }
    }
}
