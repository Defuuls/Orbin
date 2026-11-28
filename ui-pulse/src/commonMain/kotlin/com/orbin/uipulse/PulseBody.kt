package com.orbin.uipulse

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink

private const val QUOTE_MARK = ">"

/** The tag of the link annotation drawn for a reference, so tests and screen readers can find it. */
internal fun refTag(postNo: Long): String = "ref:$postNo"

/**
 * One body line as styled text. A reference is a link that calls [onRef]; everything else is
 * appended as plain text. Greentext keeps its leading `>` so it reads as a quote without colour.
 */
internal fun annotateBody(
    line: PulseBodyLine,
    refStyle: SpanStyle,
    onRef: (Long) -> Unit,
): AnnotatedString =
    buildAnnotatedString {
        if (line.quote) append(QUOTE_MARK)
        line.parts.forEach { part ->
            when (part) {
                is PulsePart.Text -> append(part.value)
                is PulsePart.Ref ->
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = refTag(part.postNo),
                            styles = TextLinkStyles(style = refStyle),
                            linkInteractionListener = { onRef(part.postNo) },
                        ),
                    ) { append(part.label) }
            }
        }
    }

/** The index of the line showing post [postNo], or -1 when there is none (or [postNo] is not a post). */
internal fun lineIndexOfNumber(
    lines: List<PulseLineUi>,
    postNo: Long,
): Int = if (postNo <= 0L) -1 else lines.indexOfFirst { it.number == postNo }
