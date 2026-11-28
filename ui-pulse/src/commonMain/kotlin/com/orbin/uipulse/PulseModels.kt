package com.orbin.uipulse

/** One line of the stream, already formatted for display. The screens never see domain types. */
data class PulseLineUi(
    val id: String,
    val age: String,
    val title: String,
    val sub: String,
    val trailing: String = "",
    val unread: Boolean = false,
    /** The post number this line shows, so a reference to it can scroll here. Zero for other lines. */
    val number: Long = 0L,
    /** The text of a post. When present it is shown in place of [title], line by line. */
    val body: List<PulseBodyLine> = emptyList(),
)

/** One line of a post's text. Greentext is [quote]. */
data class PulseBodyLine(
    val parts: List<PulsePart>,
    val quote: Boolean = false,
)

/** A piece of a body line. Never markup: it is always drawn as plain text. */
sealed interface PulsePart {
    data class Text(
        val value: String,
    ) : PulsePart

    /** A reference such as `>>123` to another line of the same stream, tappable. */
    data class Ref(
        val label: String,
        val postNo: Long,
    ) : PulsePart
}

/** One view the person can switch to from the controls. */
data class PulseLensUi(
    val id: String,
    val label: String,
    val selected: Boolean,
)

/** One suggestion under the command line. */
data class PulseCommandUi(
    val id: String,
    val label: String,
)

/** Every word the controls show, supplied by the host so the screens stay free of resources. */
data class PulseControlsLabels(
    val title: String,
    val refresh: String,
    val back: String,
    val commandHint: String,
    val close: String,
)
