package com.orbin.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateChanged
import platform.UIKit.UIHoverGestureRecognizer
import platform.UIKit.UIPencilInteraction
import platform.UIKit.UIPencilInteractionDelegateProtocol
import platform.UIKit.UIPencilInteractionPhase
import platform.UIKit.UIPencilInteractionSqueeze
import platform.UIKit.UIPencilInteractionTap
import platform.UIKit.UIView
import platform.UIKit.addInteraction
import platform.darwin.NSObject

/**
 * Listens for the Apple Pencil on [view], the app's root: the barrel double-tap and the Pro's
 * squeeze, and the tip hovering over the screen with its roll. Each arrives at [Pencil]. On an
 * iPhone, or an iPad with no Pencil, none of these ever fire.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun attachPencil(view: UIView) {
    view.addInteraction(UIPencilInteraction().apply { delegate = PencilListener })
    view.addGestureRecognizer(
        UIHoverGestureRecognizer(target = PencilListener, action = NSSelectorFromString("onHover:")),
    )
}

@OptIn(ExperimentalForeignApi::class)
private object PencilListener : NSObject(), UIPencilInteractionDelegateProtocol {
    /** The double-tap before iOS 17.5. */
    override fun pencilInteractionDidTap(interaction: UIPencilInteraction) {
        Pencil.gesture(PencilGesture.DOUBLE_TAP)
    }

    /** The double-tap from iOS 17.5, which the system calls instead of the one above. */
    override fun pencilInteraction(
        interaction: UIPencilInteraction,
        didReceiveTap: UIPencilInteractionTap,
    ) {
        Pencil.gesture(PencilGesture.DOUBLE_TAP)
    }

    /** The Pro's squeeze, counted once, when it is let go. */
    override fun pencilInteraction(
        interaction: UIPencilInteraction,
        didReceiveSqueeze: UIPencilInteractionSqueeze,
    ) {
        if (didReceiveSqueeze.phase == UIPencilInteractionPhase.UIPencilInteractionPhaseEnded) {
            Pencil.gesture(PencilGesture.SQUEEZE)
        }
    }

    @ObjCAction
    fun onHover(recognizer: UIHoverGestureRecognizer) {
        when (recognizer.state) {
            UIGestureRecognizerStateBegan,
            UIGestureRecognizerStateChanged,
            -> {
                // The roll is only on the Pro, from iOS 17.5; asking an older system would crash.
                val roll =
                    if (recognizer.respondsToSelector(NSSelectorFromString("rollAngle"))) {
                        recognizer.rollAngle.toFloat()
                    } else {
                        null
                    }
                recognizer.locationInView(recognizer.view).useContents {
                    Pencil.hoverAt(PencilHover(x.toFloat(), y.toFloat(), roll))
                }
            }
            else -> Pencil.hoverAt(null)
        }
    }
}
