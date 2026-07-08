package com.orbin.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand color tokens and semantic accents. Dynamic color is preferred on Android 12+ when enabled;
 * these static tokens feed the fallback Material 3 schemes and the AMOLED variant.
 */

internal val OrbinPrimary = Color(0xFF4F6BED)
internal val OrbinOnPrimary = Color(0xFFFFFFFF)
internal val OrbinPrimaryContainer = Color(0xFFDDE1FF)
internal val OrbinOnPrimaryContainer = Color(0xFF001257)

internal val OrbinSecondary = Color(0xFF5A5D72)
internal val OrbinOnSecondary = Color(0xFFFFFFFF)
internal val OrbinSecondaryContainer = Color(0xFFDFE1F9)
internal val OrbinOnSecondaryContainer = Color(0xFF171B2C)

internal val OrbinTertiary = Color(0xFF75546F)
internal val OrbinOnTertiary = Color(0xFFFFFFFF)
internal val OrbinTertiaryContainer = Color(0xFFFFD7F4)
internal val OrbinOnTertiaryContainer = Color(0xFF2C122A)

internal val OrbinError = Color(0xFFBA1A1A)
internal val OrbinOnError = Color(0xFFFFFFFF)
internal val OrbinErrorContainer = Color(0xFFFFDAD6)
internal val OrbinOnErrorContainer = Color(0xFF410002)

internal val OrbinBackground = Color(0xFFFEFBFF)
internal val OrbinOnBackground = Color(0xFF1B1B21)
internal val OrbinSurface = Color(0xFFFEFBFF)
internal val OrbinOnSurface = Color(0xFF1B1B21)
internal val OrbinSurfaceVariant = Color(0xFFE2E1EC)
internal val OrbinOnSurfaceVariant = Color(0xFF45464F)
internal val OrbinOutline = Color(0xFF767680)
internal val OrbinOutlineVariant = Color(0xFFC6C5D0)
internal val OrbinInverseSurface = Color(0xFF303036)
internal val OrbinInverseOnSurface = Color(0xFFF2F0F7)
internal val OrbinInversePrimary = Color(0xFFB8C3FF)
internal val OrbinScrim = Color(0xFF000000)

internal val OrbinSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val OrbinSurfaceContainerLow = Color(0xFFF8F6FD)
internal val OrbinSurfaceContainer = Color(0xFFF2F0F7)
internal val OrbinSurfaceContainerHigh = Color(0xFFECEAF1)
internal val OrbinSurfaceContainerHighest = Color(0xFFE6E4EB)

internal val OrbinDarkPrimary = Color(0xFFB8C3FF)
internal val OrbinDarkOnPrimary = Color(0xFF102EA1)
internal val OrbinDarkPrimaryContainer = Color(0xFF334BBF)
internal val OrbinDarkOnPrimaryContainer = Color(0xFFDDE1FF)

internal val OrbinDarkSecondary = Color(0xFFC3C5DD)
internal val OrbinDarkOnSecondary = Color(0xFF2B3042)
internal val OrbinDarkSecondaryContainer = Color(0xFF424659)
internal val OrbinDarkOnSecondaryContainer = Color(0xFFDFE1F9)

internal val OrbinDarkTertiary = Color(0xFFE3BADA)
internal val OrbinDarkOnTertiary = Color(0xFF432740)
internal val OrbinDarkTertiaryContainer = Color(0xFF5B3D57)
internal val OrbinDarkOnTertiaryContainer = Color(0xFFFFD7F4)

internal val OrbinDarkError = Color(0xFFFFB4AB)
internal val OrbinDarkOnError = Color(0xFF690005)
internal val OrbinDarkErrorContainer = Color(0xFF93000A)
internal val OrbinDarkOnErrorContainer = Color(0xFFFFDAD6)

internal val OrbinDarkBackground = Color(0xFF131318)
internal val OrbinDarkOnBackground = Color(0xFFE4E1E9)
internal val OrbinDarkSurface = Color(0xFF131318)
internal val OrbinDarkOnSurface = Color(0xFFE4E1E9)
internal val OrbinDarkSurfaceVariant = Color(0xFF45464F)
internal val OrbinDarkOnSurfaceVariant = Color(0xFFC6C5D0)
internal val OrbinDarkOutline = Color(0xFF90909A)
internal val OrbinDarkOutlineVariant = Color(0xFF45464F)
internal val OrbinDarkInverseSurface = Color(0xFFE4E1E9)
internal val OrbinDarkInverseOnSurface = Color(0xFF303036)
internal val OrbinDarkInversePrimary = OrbinPrimary

internal val OrbinDarkSurfaceContainerLowest = Color(0xFF0E0E13)
internal val OrbinDarkSurfaceContainerLow = Color(0xFF1B1B21)
internal val OrbinDarkSurfaceContainer = Color(0xFF1F1F25)
internal val OrbinDarkSurfaceContainerHigh = Color(0xFF29292F)
internal val OrbinDarkSurfaceContainerHighest = Color(0xFF34343A)

// Semantic accents used by the post renderer (greentext, quote links, spoilers).
val GreentextColor = Color(0xFF789922)
val QuoteLinkColor = Color(0xFF4F6BED)
val SpoilerBackground = Color(0xFF2B2B2B)

// Status accents for future shared UI states.
val SuccessColor = Color(0xFF2E7D32)
val WarningColor = Color(0xFFB26A00)
val InfoColor = OrbinPrimary

// Tomorrow theme (https://github.com/chriskempson/tomorrow-theme) light colors
internal val TomorrowPrimary = Color(0xFF4271AE)
internal val TomorrowOnPrimary = Color(0xFFFFFFFF)
internal val TomorrowPrimaryContainer = Color(0xFFD5E0FB)
internal val TomorrowOnPrimaryContainer = Color(0xFF0D1E52)

internal val TomorrowSecondary = Color(0xFF718C00)
internal val TomorrowOnSecondary = Color(0xFFFFFFFF)
internal val TomorrowSecondaryContainer = Color(0xFFE8F7D4)
internal val TomorrowOnSecondaryContainer = Color(0xFF1E2600)

internal val TomorrowTertiary = Color(0xFF9B3F84)
internal val TomorrowOnTertiary = Color(0xFFFFFFFF)
internal val TomorrowTertiaryContainer = Color(0xFFFDD3ED)
internal val TomorrowOnTertiaryContainer = Color(0xFF3B0D2E)

internal val TomorrowError = Color(0xFFC82829)
internal val TomorrowOnError = Color(0xFFFFFFFF)
internal val TomorrowErrorContainer = Color(0xFFFFDAD6)
internal val TomorrowOnErrorContainer = Color(0xFF410002)

internal val TomorrowBackground = Color(0xFFFAFAFA)
internal val TomorrowOnBackground = Color(0xFF1B1B1B)
internal val TomorrowSurface = Color(0xFFFAFAFA)
internal val TomorrowOnSurface = Color(0xFF1B1B1B)
internal val TomorrowSurfaceVariant = Color(0xFFE8E8E8)
internal val TomorrowOnSurfaceVariant = Color(0xFF4A4A4A)
internal val TomorrowOutline = Color(0xFF7A7A7A)
internal val TomorrowOutlineVariant = Color(0xFFC8C8C8)
internal val TomorrowInverseSurface = Color(0xFF303030)
internal val TomorrowInverseOnSurface = Color(0xFFF5F5F5)
internal val TomorrowInversePrimary = Color(0xFF99BEFF)
internal val TomorrowScrim = Color(0xFF000000)

internal val TomorrowSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val TomorrowSurfaceContainerLow = Color(0xFFF5F5F5)
internal val TomorrowSurfaceContainer = Color(0xFFEFEFEF)
internal val TomorrowSurfaceContainerHigh = Color(0xFFE9E9E9)
internal val TomorrowSurfaceContainerHighest = Color(0xFFE3E3E3)

// Tomorrow Night theme dark colors
internal val TomorrowNightPrimary = Color(0xFF99BEFF)
internal val TomorrowNightOnPrimary = Color(0xFF122C5C)
internal val TomorrowNightPrimaryContainer = Color(0xFF284176)
internal val TomorrowNightOnPrimaryContainer = Color(0xFFD5E0FB)

internal val TomorrowNightSecondary = Color(0xFFCCE5A5)
internal val TomorrowNightOnSecondary = Color(0xFF2D3B05)
internal val TomorrowNightSecondaryContainer = Color(0xFF43520C)
internal val TomorrowNightOnSecondaryContainer = Color(0xFFE8F7D4)

internal val TomorrowNightTertiary = Color(0xFFF5B8DD)
internal val TomorrowNightOnTertiary = Color(0xFF52223E)
internal val TomorrowNightTertiaryContainer = Color(0xFF6B3A55)
internal val TomorrowNightOnTertiaryContainer = Color(0xFFFDD3ED)

internal val TomorrowNightError = Color(0xFFFFB4AB)
internal val TomorrowNightOnError = Color(0xFF690005)
internal val TomorrowNightErrorContainer = Color(0xFF93000A)
internal val TomorrowNightOnErrorContainer = Color(0xFFFFDAD6)

internal val TomorrowNightBackground = Color(0xFF1D1D1D)
internal val TomorrowNightOnBackground = Color(0xFFE4E4E4)
internal val TomorrowNightSurface = Color(0xFF1D1D1D)
internal val TomorrowNightOnSurface = Color(0xFFE4E4E4)
internal val TomorrowNightSurfaceVariant = Color(0xFF2F2F2F)
internal val TomorrowNightOnSurfaceVariant = Color(0xFFC5C5C5)
internal val TomorrowNightOutline = Color(0xFF909090)
internal val TomorrowNightOutlineVariant = Color(0xFF3A3A3A)
internal val TomorrowNightInverseSurface = Color(0xFFE4E4E4)
internal val TomorrowNightInverseOnSurface = Color(0xFF1D1D1D)
internal val TomorrowNightInversePrimary = Color(0xFF4271AE)

internal val TomorrowNightSurfaceContainerLowest = Color(0xFF151515)
internal val TomorrowNightSurfaceContainerLow = Color(0xFF262626)
internal val TomorrowNightSurfaceContainer = Color(0xFF2A2A2A)
internal val TomorrowNightSurfaceContainerHigh = Color(0xFF343434)
internal val TomorrowNightSurfaceContainerHighest = Color(0xFF3F3F3F)
