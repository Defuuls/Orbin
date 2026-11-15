package com.orbin.uinext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.next_settings_no_matches
import com.orbin.uinext.tokens.OrbinElevation
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderSize
import org.jetbrains.compose.resources.stringResource

internal val preferenceTitleStyle: TextStyle
    @Composable get() =
        OrbinType.stateBody.withReaderFont().copy(
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
        )

internal val preferenceSummaryStyle: TextStyle
    @Composable get() =
        OrbinType.preview.withReaderFont().copy(
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Normal,
        )

/** Settings surfaces and text stay independent from post-reading colors. */
internal val preferencePrimaryColor: Color
    @Composable get() = settingsStyle.text

/** Anchored choices retain one labeled radio action and the app's resolved text density. */
@Composable
internal fun ReaderSettingAnchor(
    item: SettingItem,
    expanded: Boolean,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (item.kind != SettingKind.CHOICE || item.id == "playbackScope" || item.options.size > 8) {
        content()
        return
    }
    val density = LocalDensity.current
    val colors = settingsStyle
    val offset = with(density) { 8.dp.roundToPx() }
    val position = remember(offset) { PreferencePopupPosition(offset) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val menuWidth = maxWidth.coerceAtMost(250.dp)
        content()
        if (expanded) {
            Popup(
                popupPositionProvider = position,
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = true),
            ) {
                // The platform popup installs its own density. Preserve the already resolved
                // app/system text scale, including the iOS Dynamic Type correction, exactly once.
                CompositionLocalProvider(LocalDensity provides density) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = colors.card,
                        shadowElevation = OrbinElevation.floating,
                        border = androidx.compose.foundation.BorderStroke(ReaderSize.hairline, colors.border),
                    ) {
                        Column(
                            Modifier
                                .testTag("reader-settings-choice-popup")
                                .width(menuWidth)
                                .heightIn(max = ReaderSize.readingMax)
                                .verticalScroll(rememberScrollState())
                                .padding(6.dp)
                                .selectableGroup(),
                        ) {
                            if (item.options.isEmpty()) {
                                Text(
                                    stringResource(Res.string.next_settings_no_matches),
                                    style = preferenceTitleStyle,
                                    color = preferencePrimaryColor,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                            item.options.forEachIndexed { index, label ->
                                val isSelected = index == item.selected
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .background(
                                            if (isSelected) colors.accent.copy(alpha = 0.12f) else Color.Transparent,
                                            RoundedCornerShape(15.dp),
                                        ).selectable(
                                            selected = isSelected,
                                            role = Role.RadioButton,
                                            onClick = { onSelect(index) },
                                        ).padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        label,
                                        style = preferenceTitleStyle,
                                        color = preferencePrimaryColor,
                                        modifier = Modifier.weight(1f),
                                    )
                                    ReaderSettingsChoiceMark(selected = isSelected)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The selectable row owns state and interaction; this mark is decorative for accessibility. */
@Composable
internal fun ReaderSettingsChoiceMark(selected: Boolean) {
    val colors = settingsStyle
    Canvas(Modifier.size(20.dp).clearAndSetSemantics {}) {
        if (selected) {
            val tick =
                Path().apply {
                    moveTo(4.dp.toPx(), 10.dp.toPx())
                    lineTo(8.dp.toPx(), 14.dp.toPx())
                    lineTo(16.dp.toPx(), 5.dp.toPx())
                }
            drawPath(
                tick,
                color = colors.accent,
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** Compact choices stay in place; each segment owns its complete accessible radio action. */
@Composable
internal fun SettingsSegments(
    item: SettingItem,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = settingsStyle
    Row(
        modifier =
            modifier
                .background(colors.background, RoundedCornerShape(12.dp))
                .padding(3.dp)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.options.forEachIndexed { index, label ->
            val selected = index == item.selected
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .background(
                            if (selected) colors.accent else Color.Transparent,
                            RoundedCornerShape(9.dp),
                        ).selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style =
                        preferenceTitleStyle.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        ),
                    color = if (selected) colors.onAccent else colors.text,
                )
            }
        }
    }
}

/** Align menus to their row and prefer opening below, with an eight-dp gap. */
internal class PreferencePopupPosition(
    private val offset: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val margin = (offset * POPUP_EDGE_GAP_RATIO).toInt()
        val x =
            if (layoutDirection == LayoutDirection.Rtl) {
                anchorBounds.right - popupContentSize.width
            } else {
                anchorBounds.left
            }
        val below = anchorBounds.bottom + offset
        val above = anchorBounds.top - popupContentSize.height - offset
        val roomBelow = windowSize.height - below - margin
        val roomAbove = anchorBounds.top - margin - offset
        val y = if (popupContentSize.height > roomBelow && roomAbove > roomBelow) above else below
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(0)
        return IntOffset(
            x.coerceIn(margin.coerceAtMost(maxX), maxX),
            y.coerceIn(margin.coerceAtMost(maxY), maxY),
        )
    }
}

private const val POPUP_EDGE_GAP_RATIO = 1.5f
