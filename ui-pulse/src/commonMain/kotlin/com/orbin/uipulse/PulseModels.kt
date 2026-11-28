package com.orbin.uipulse

/** One line of the stream, already formatted for display. The screens never see domain types. */
data class PulseLineUi(
    val id: String,
    val age: String,
    val title: String,
    val sub: String,
    val trailing: String = "",
    val unread: Boolean = false,
)

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
