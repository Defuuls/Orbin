package com.orbin.uinext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_pane_thread
import com.orbin.uinext.resources.reader_select_thread
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderSize
import org.jetbrains.compose.resources.stringResource

object ReaderTwoPaneTags {
    const val CATALOG = "reader-catalog-pane"
    const val DIVIDER = "reader-pane-divider"
    const val DETAIL = "reader-thread-pane"
    const val EMPTY = "reader-empty-thread-pane"
    const val EMPTY_HEADER = "reader-empty-thread-header"
    const val EMPTY_BODY = "reader-empty-thread-body"
    const val EMPTY_CARD = "reader-empty-thread-card"
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
    Column(
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
    ) {
        Box(Modifier.fillMaxWidth().testTag(ReaderTwoPaneTags.EMPTY_HEADER)) {
            ReaderAppBar(title = stringResource(Res.string.reader_pane_thread))
        }
        Box(
            Modifier.fillMaxWidth().weight(1f).testTag(ReaderTwoPaneTags.EMPTY_BODY),
            contentAlignment = Alignment.Center,
        ) {
            OrbinCard(
                modifier =
                    Modifier
                        .padding(24.dp)
                        .widthIn(max = 360.dp)
                        .fillMaxWidth()
                        .testTag(ReaderTwoPaneTags.EMPTY_CARD),
                border = BorderStroke(1.dp, colors.hairline),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OrbinLogo(size = 48.dp, contentDescription = null)
                    Text(
                        text = stringResource(Res.string.reader_select_thread),
                        modifier = Modifier.testTag(ReaderTwoPaneTags.EMPTY).semantics { heading() },
                        color = colors.inkMuted,
                        style = OrbinType.stateBody.withReaderFont(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
