package com.orbin.uinext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
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
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderSize
import org.jetbrains.compose.resources.stringResource

internal val preferenceTitleStyle: TextStyle
    @Composable get() =
        OrbinType.stateBody.withReaderFont().copy(
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
        )

internal val preferenceSummaryStyle: TextStyle
    @Composable get() =
        OrbinType.preview.withReaderFont().copy(
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Normal,
        )

/** Preference text follows the native neutral theme, separately from post text colors. */
internal val preferencePrimaryColor: Color
    @Composable get() = if (next.dark) Color.White else Color(0xDE000000)

/** Coral preference choices share their row's origin and keep one labeled radio action. */
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
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val offset = with(density) { 5.dp.roundToPx() }
    val position = remember(offset) { PreferencePopupPosition(offset) }
    val menuWidth = with(density) { anchorSize.width.toDp().coerceIn(224.dp, 448.dp) }
    Box(Modifier.fillMaxWidth().onSizeChanged { anchorSize = it }) {
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
                        shape = RoundedCornerShape(OrbinRadius.card),
                        color = if (next.dark) reader.surfaceVariant else reader.background,
                        shadowElevation = OrbinElevation.floating,
                        border = androidx.compose.foundation.BorderStroke(ReaderSize.hairline, reader.hairline),
                    ) {
                        Column(
                            Modifier
                                .testTag("reader-settings-choice-popup")
                                .width(menuWidth)
                                .heightIn(max = ReaderSize.readingMax)
                                .verticalScroll(rememberScrollState())
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
                                        .selectable(
                                            selected = isSelected,
                                            role = Role.RadioButton,
                                            onClick = { onSelect(index) },
                                        ).padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
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
    val colors = reader
    Canvas(Modifier.size(20.dp).clearAndSetSemantics {}) {
        drawCircle(
            color = if (selected) colors.accent else colors.inkSecondary,
            radius = 9.dp.toPx(),
            style = Stroke(2.dp.toPx()),
        )
        if (selected) drawCircle(color = colors.accent, radius = 4.dp.toPx())
    }
}

/** Five pixels in the 160dpi reference are five dp; clamp only to keep the complete menu on screen. */
internal class PreferencePopupPosition(
    private val offset: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x =
            if (layoutDirection == LayoutDirection.Rtl) {
                anchorBounds.right - popupContentSize.width - offset
            } else {
                anchorBounds.left + offset
            }
        val y = anchorBounds.top + offset
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}
