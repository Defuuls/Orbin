package com.orbin.uipulse

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseBodyTest {
    private val style = SpanStyle()

    private fun line(
        vararg parts: PulsePart,
        quote: Boolean = false,
    ) = PulseBodyLine(parts.toList(), quote)

    @Test
    fun plainTextIsAppendedAsIs() {
        val text = annotateBody(line(PulsePart.Text("hello "), PulsePart.Text("world")), style) {}
        assertEquals("hello world", text.text)
        assertTrue(text.getLinkAnnotations(0, text.length).isEmpty())
    }

    @Test
    fun greentextKeepsItsLeadingMarkSoItReadsWithoutColour() {
        val text = annotateBody(line(PulsePart.Text("be me"), quote = true), style) {}
        assertEquals(">be me", text.text)
    }

    @Test
    fun aReferenceIsALinkOverItsLabelAndCallsBackWithItsPost() {
        val tapped = mutableListOf<Long>()
        val text =
            annotateBody(line(PulsePart.Text("see "), PulsePart.Ref(">>123", 123L), PulsePart.Text(" ok")), style) {
                tapped += it
            }
        assertEquals("see >>123 ok", text.text)
        val links = text.getLinkAnnotations(0, text.length)
        assertEquals(1, links.size)
        val link = links.single()
        assertEquals(4, link.start)
        assertEquals(9, link.end)
        val clickable = link.item as LinkAnnotation.Clickable
        assertEquals(refTag(123L), clickable.tag)
        clickable.linkInteractionListener!!.onClick(clickable)
        assertEquals(listOf(123L), tapped)
    }

    @Test
    fun everyReferenceOnALineIsItsOwnLink() {
        val text = annotateBody(line(PulsePart.Ref(">>1", 1L), PulsePart.Text(" "), PulsePart.Ref(">>2", 2L)), style) {}
        assertEquals(2, text.getLinkAnnotations(0, text.length).size)
    }

    @Test
    fun hostileTextStaysPlainText() {
        val text = annotateBody(line(PulsePart.Text("<script>alert(1)</script> [url=x]")), style) {}
        assertEquals("<script>alert(1)</script> [url=x]", text.text)
        assertTrue(text.getLinkAnnotations(0, text.length).isEmpty())
    }

    @Test
    fun aReferenceFindsItsLineByNumber() {
        val lines = listOf(PulseLineUi("p:1", "", "", "", number = 1L), PulseLineUi("p:7", "", "", "", number = 7L))
        assertEquals(1, lineIndexOfNumber(lines, 7L))
        assertEquals(0, lineIndexOfNumber(lines, 1L))
        assertEquals(-1, lineIndexOfNumber(lines, 9L))
    }

    @Test
    fun noLineIsFoundForAnInvalidNumber() {
        val lines = listOf(PulseLineUi("t:a|g|1", "", "", ""), PulseLineUi("p:2", "", "", "", number = 2L))
        assertEquals(-1, lineIndexOfNumber(lines, 0L))
        assertEquals(-1, lineIndexOfNumber(lines, -5L))
        assertEquals(-1, lineIndexOfNumber(emptyList(), 3L))
    }
}
