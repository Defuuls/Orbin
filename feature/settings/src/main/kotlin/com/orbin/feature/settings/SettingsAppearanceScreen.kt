package com.orbin.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.designsystem.component.ModernSmallTopAppBar
import com.orbin.core.model.AppIconVariant
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.ColorTheme
import com.orbin.core.model.ThreadPresentation
import com.orbin.core.model.ThumbnailSize

/** Theme, color, layout, and text-size settings — how the app looks, not what it shows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAppearanceScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            ModernSmallTopAppBar(
                title = "Appearance",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
        ) {
            ColorThemeRow(settings.colorTheme, viewModel::setColorTheme)
            AppIconVariantRow(settings.appIconVariant, viewModel::setAppIconVariant)
            ThemeModeRow(settings.themeMode, viewModel::setThemeMode)
            SwitchRow("Dynamic color", settings.dynamicColor, viewModel::setDynamicColor)
            SwitchRow("AMOLED black", settings.amoled, viewModel::setAmoled)
            ChoiceRow(
                label = "Open threads as",
                values = ThreadPresentation.entries,
                selected = settings.threadPresentation,
                text = { it.label },
                onChange = viewModel::setThreadPresentation,
            )
            SupportingNote(
                "\"Page\" pushes the thread and takes the feed with it. \"Slide over\" lays the " +
                    "thread on top, leaving the feed in place underneath so going back reveals it " +
                    "rather than sliding it back.",
            )
            SwitchRow(
                "Full-screen feed",
                settings.fullScreenFeedChrome,
                viewModel::setFullScreenFeedChrome,
                supporting = "Hide the board headers, feed bars, and system bars so the feed fills the whole screen.",
            )
            ChoiceRow(
                label = "Font size",
                values = FontScaleOption.entries,
                selected = FontScaleOption.fromScale(settings.fontScale),
                text = { it.label },
                onChange = { option -> viewModel.setFontScale(option.scale) },
            )
            ChoiceRow(
                label = "Thumbnail size",
                values = ThumbnailSize.entries,
                selected = settings.thumbnailSize,
                text = { it.label },
                onChange = viewModel::setThumbnailSize,
            )
        }
    }
}

@Composable
private fun ColorThemeRow(
    current: ColorTheme,
    onChange: (ColorTheme) -> Unit,
) {
    ChoiceRow(
        label = "Color theme",
        values = ColorTheme.entries,
        selected = current,
        text = { it.name.lowercase().replaceFirstChar { char -> char.uppercase() } },
        onChange = onChange,
    )
}

@Composable
private fun AppIconVariantRow(
    current: AppIconVariant,
    onChange: (AppIconVariant) -> Unit,
) {
    ChoiceRow(
        label = "App icon",
        values = AppIconVariant.entries,
        selected = current,
        text = { it.name.lowercase().replaceFirstChar { char -> char.uppercase() } },
        onChange = onChange,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeRow(
    current: AppThemeMode,
    onChange: (AppThemeMode) -> Unit,
) {
    ChoiceRow(
        label = "Theme",
        values = AppThemeMode.entries,
        selected = current,
        text = { it.name.lowercase().replaceFirstChar { char -> char.uppercase() } },
        onChange = onChange,
    )
}

private const val FONT_SCALE_SMALL = 0.9f
private const val FONT_SCALE_DEFAULT = 1f
private const val FONT_SCALE_LARGE = 1.1f
private const val FONT_SCALE_EXTRA_LARGE = 1.2f

private enum class FontScaleOption(
    val scale: Float,
    val label: String,
) {
    SMALL(FONT_SCALE_SMALL, "Small"),
    DEFAULT(FONT_SCALE_DEFAULT, "Default"),
    LARGE(FONT_SCALE_LARGE, "Large"),
    XLARGE(FONT_SCALE_EXTRA_LARGE, "XL"),
    ;

    companion object {
        fun fromScale(scale: Float): FontScaleOption =
            entries.minByOrNull { option -> kotlin.math.abs(option.scale - scale) } ?: DEFAULT
    }
}
