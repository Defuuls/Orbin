package com.orbin.core.model

import kotlinx.serialization.Serializable

/** Stable persisted names. White and Black have fixed brightness; other colors follow Theme mode. */
@Serializable
enum class AppColorTheme(
    val label: String,
) {
    ORBIN("Orbin (green)"),
    BLUE("Blue"),
    RED("Red"),
    WHITE("White (always light)"),
    BLACK("Black (always dark)"),
    PURPLE("Purple"),
    ORANGE("Orange"),
    TEAL("Teal"),
}
