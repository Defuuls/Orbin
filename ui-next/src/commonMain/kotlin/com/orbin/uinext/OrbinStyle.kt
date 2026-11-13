package com.orbin.uinext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbin.uinext.tokens.OrbinElevation
import com.orbin.uinext.tokens.OrbinRadius
import com.orbin.uinext.tokens.ReaderType

/**
 * Shared Orbin Coral chrome: the coral ring mark, wordmark, and softly elevated cards.
 *
 * Screens opt into these primitives as they are restyled. They do not, by themselves, replace
 * the Clover-derived reader cells.
 */
object OrbinStyle {
    const val WORDMARK = "Orbin"
}

/** The coral ring, tinted with the current accent by default. */
@Composable
fun OrbinLogo(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = reader.accent,
    contentDescription: String? = OrbinStyle.WORDMARK,
) {
    Icon(
        imageVector = ReaderIcons.Logo,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/**
 * Logo plus the "Orbin" wordmark, as used on the Boards home header and the media viewer.
 *
 * [onInk] is the wordmark color; the ring always uses [tint] so it stays coral on both
 * light chrome and the dark media viewer.
 */
@Composable
fun OrbinWordmark(
    modifier: Modifier = Modifier,
    logoSize: Dp = 28.dp,
    tint: Color = reader.accent,
    onInk: Color = reader.ink,
    contentDescription: String = OrbinStyle.WORDMARK,
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OrbinLogo(size = logoSize, tint = tint, contentDescription = null)
        Text(
            text = OrbinStyle.WORDMARK,
            style = ReaderType.appBarTitle.withReaderFont().copy(fontWeight = FontWeight.SemiBold),
            color = onInk,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * A white (or dark-elevated) card with the Coral corner radius and a soft shadow.
 *
 * On light themes the lift is a 2dp drop shadow over the page ground. On dark and AMOLED
 * themes the shadow is dropped in favor of a tonal elevation so the card does not halo.
 */
@Composable
fun OrbinCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    shape: Shape = RoundedCornerShape(OrbinRadius.card),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shadow = if (next.dark) 0.dp else OrbinElevation.card
    val tonal = if (next.dark) OrbinElevation.card else 0.dp
    val color = if (next.dark) next.elevated else next.raised
    Surface(
        modifier =
            if (onClick != null) {
                modifier.nextClickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            } else {
                modifier
            },
        shape = shape,
        color = color,
        shadowElevation = shadow,
        tonalElevation = tonal,
        content = { Column(content = content) },
    )
}
