package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderType
import org.jetbrains.compose.resources.stringResource

internal val preferenceTitleStyle: TextStyle
    @Composable get() =
        ReaderType.stateBody.withReaderFont().copy(
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
        )

internal val preferenceSummaryStyle: TextStyle
    @Composable get() =
        ReaderType.preview.withReaderFont().copy(
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Normal,
        )

/** Preference text follows the native neutral theme, separately from post text colors. */
internal val preferencePrimaryColor: Color
    @Composable get() = if (next.dark) Color.White else Color(0xDE000000)

/** The floating preference menu shares its row's origin and has no dimmed background or title. */
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
                Surface(
                    shape = RectangleShape,
                    color = reader.background,
                    shadowElevation = 8.dp,
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
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(
                                        selected = index == item.selected,
                                        role = Role.RadioButton,
                                        onClick = { onSelect(index) },
                                    ).padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(label, style = preferenceTitleStyle, color = preferencePrimaryColor)
                            }
                        }
                    }
                }
            }
        }
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
