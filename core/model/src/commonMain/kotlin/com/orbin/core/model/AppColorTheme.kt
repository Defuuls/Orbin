package com.orbin.core.model

import kotlinx.serialization.Serializable

/** Stable persisted names. All colors follow Theme mode and the independent True black preference. */
@Serializable
enum class AppColorTheme(
    val label: String,
) {
    ORBIN("Orbin (green)"),
    BLUE("Blue"),
    RED("Red"),
    WHITE("White"),
    BLACK("Black"),
    PURPLE("Purple"),
    ORANGE("Orange"),
    TEAL("Teal"),
}
