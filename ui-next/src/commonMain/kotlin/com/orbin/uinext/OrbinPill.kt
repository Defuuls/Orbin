package com.orbin.uinext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbin.uinext.resources.Res
import com.orbin.uinext.resources.reader_unread
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.OrbinType
import com.orbin.uinext.tokens.ReaderColors
import com.orbin.uinext.tokens.ReaderSize
import org.jetbrains.compose.resources.pluralStringResource

/** The looks a [OrbinPill] can take. Every pill and badge in the app is one of these. */
enum class PillVariant {
    /** Accent-filled, with [ReaderColors.onAccent] text: unread counts and pill buttons. */
    Filled,

    /** A wash of a tint with the tint as text: kinds, sources, counters on the app's surfaces. */
    Tinted,

    /** White on a dark scrim, so it reads over any image. */
    Media,
}

/** The ground and text colours of a pill, resolved from the theme. Exposed for the contrast test. */
data class PillColors(
    val container: Color,
    val content: Color,
)

/** Wash strength of a [PillVariant.Tinted] pill. */
const val PILL_TINT_ALPHA = 0.14f
private const val PILL_INK_DEEPEN = 0.2f
private const val LIGHT_GROUND = 0.5f

/** Scrim strength of a [PillVariant.Media] pill. */
const val PILL_MEDIA_SCRIM_ALPHA = 0.55f

fun pillColors(
    variant: PillVariant,
    colors: ReaderColors,
    tint: Color? = null,
): PillColors =
    when (variant) {
        PillVariant.Filled -> PillColors(tint ?: colors.accent, colors.onAccent)
        PillVariant.Tinted -> {
            val hue = tint ?: colors.accent
            // On light grounds a deeper tone of the hue keeps the text at AA over its own tint.
            val ink = if (colors.background.luminance() > LIGHT_GROUND) lerp(hue, Color.Black, PILL_INK_DEEPEN) else hue
            PillColors(hue.copy(alpha = PILL_TINT_ALPHA), ink)
        }
        PillVariant.Media -> PillColors(Color.Black.copy(alpha = PILL_MEDIA_SCRIM_ALPHA), Color.White)
    }

/**
 * The one pill component. A small rounded label with an optional leading icon. With [onClick]
 * it becomes a full-size pill button that keeps the minimum touch target.
 */
@Composable
fun OrbinPill(
    text: String,
    modifier: Modifier = Modifier,
    variant: PillVariant = PillVariant.Tinted,
    tint: Color? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val resolved = pillColors(variant, reader, tint)
    val shape = RoundedCornerShape(OrbinRadius.pill)
    val button = onClick != null
    val style =
        if (button) {
            OrbinType.labelLarge.copy(fontWeight = FontWeight.SemiBold)
        } else {
            OrbinType.caption2.withReaderFont().copy(fontWeight = FontWeight.SemiBold)
        }
    Row(
        modifier =
            modifier
                .width(IntrinsicSize.Max)
                .then(if (button) Modifier.heightIn(min = ReaderSize.touchTarget) else Modifier)
                .clip(shape)
                .background(resolved.container)
                .then(
                    if (onClick != null) {
                        Modifier
                            .nextClickable(role = Role.Button, onClick = onClick)
                            .semantics { contentDescription = text }
                    } else {
                        Modifier
                    },
                ).padding(
                    horizontal = if (button) PILL_BUTTON_PAD_X else PILL_PAD_X,
                    vertical = if (button) PILL_BUTTON_PAD_Y else PILL_PAD_Y,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PILL_ICON_GAP),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = resolved.content, modifier = Modifier.size(PILL_ICON_SIZE))
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = style,
            color = resolved.content,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The one unread label: an accent-filled pill reading "N unread", the same on every surface. */
@Composable
fun UnreadLabel(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    OrbinPill(
        text = pluralStringResource(Res.plurals.reader_unread, count, count),
        modifier = modifier,
        variant = PillVariant.Filled,
    )
}

private val PILL_PAD_X = 8.dp
private val PILL_PAD_Y = 3.dp
private val PILL_BUTTON_PAD_X = 16.dp
private val PILL_BUTTON_PAD_Y = 8.dp
private val PILL_ICON_GAP = 4.dp
private val PILL_ICON_SIZE = 13.dp
