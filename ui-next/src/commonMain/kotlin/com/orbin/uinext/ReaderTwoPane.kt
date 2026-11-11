package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_select_thread
import com.orbin.uinext.tokens.ReaderSize
import com.orbin.uinext.tokens.ReaderType
import org.jetbrains.compose.resources.stringResource

object ReaderTwoPaneTags {
    const val CATALOG = "reader-catalog-pane"
    const val DIVIDER = "reader-pane-divider"
    const val DETAIL = "reader-thread-pane"
    const val EMPTY = "reader-empty-thread-pane"
}

/**
 * The reader's shared catalog/thread split. Hosts supply the catalog width from the pure model
 * rule and keep ownership of their navigation. The divider and empty state belong to this frame.
 *
 * [detailContent] stays composed while no thread is open so a host's navigation graph can mount
 * before its first thread is selected. The empty state covers its drawing, input and semantics.
 */
@Composable
fun ReaderTwoPane(
    catalogWidth: Dp,
    threadOpen: Boolean,
    modifier: Modifier = Modifier,
    listContent: @Composable () -> Unit,
    detailContent: @Composable () -> Unit,
) {
    val colors = reader
    Row(modifier.fillMaxSize().background(colors.background)) {
        Box(
            Modifier
                .testTag(ReaderTwoPaneTags.CATALOG)
                .width(catalogWidth.coerceAtMost(ReaderSize.listPaneMax))
                .fillMaxHeight(),
        ) {
            listContent()
        }
        Box(
            Modifier
                .testTag(ReaderTwoPaneTags.DIVIDER)
                .width(ReaderSize.paneDivider)
                .fillMaxHeight()
                .background(colors.hairline),
        )
        Box(
            Modifier
                .testTag(ReaderTwoPaneTags.DETAIL)
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (threadOpen) Modifier else Modifier.clearAndSetSemantics {}),
            ) {
                detailContent()
            }
            if (!threadOpen) EmptyThreadPane()
        }
    }
}

@Composable
private fun EmptyThreadPane() {
    val colors = reader
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.reader_select_thread),
            modifier = Modifier.testTag(ReaderTwoPaneTags.EMPTY),
            color = colors.inkMuted,
            style = ReaderType.stateBody,
        )
    }
}
