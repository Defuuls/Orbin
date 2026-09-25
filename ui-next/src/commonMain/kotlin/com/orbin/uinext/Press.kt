package com.orbin.uinext

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.semantics.Role
import com.orbin.uinext.tokens.NextMaterials
import kotlinx.coroutines.launch

/**
 * Soft highlight on press — the iOS list-row flash, not a Material ripple.
 *
 * Installed as [androidx.compose.foundation.LocalIndication] inside [NextTheme] so default
 * `clickable` / `selectable` calls pick it up without each call site opting in.
 */
object NextHighlightIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = NextHighlightNode(interactionSource)

    // A singleton: any stable value is its identity hash. `System.identityHashCode` is JVM-only.
    override fun hashCode(): Int = NextHighlightIndication::class.hashCode()

    override fun equals(other: Any?): Boolean = other === this
}

private class NextHighlightNode(
    private val interactionSource: InteractionSource,
) : Modifier.Node(),
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        pressed = true
                        invalidateDraw()
                    }
                    is PressInteraction.Release,
                    is PressInteraction.Cancel,
                    -> {
                        pressed = false
                        invalidateDraw()
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!pressed) return
        val palette = currentValueOf(LocalNext)
        val alpha = if (palette.dark) NextMaterials.PRESS_DARK else NextMaterials.PRESS_LIGHT
        drawRect(color = palette.ink.copy(alpha = alpha))
    }
}

/**
 * Clickable with the Material 3 ripple indication tinted by current accent color.
 */
@Composable
fun Modifier.nextClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return combinedClickable(
        interactionSource = interaction,
        indication = androidx.compose.material3.ripple(color = next.accent),
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

/**
 * Icon-only control with a 48dp target and Next highlight — replaces Material IconButton.
 */
@Composable
fun NextIconAction(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = next.accent,
) {
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
                .nextClickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}
