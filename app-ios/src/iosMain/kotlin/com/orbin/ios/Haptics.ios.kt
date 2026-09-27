package com.orbin.ios

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

internal actual object Haptics {
    actual fun light() {
        UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()
    }

    actual fun medium() {
        UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).impactOccurred()
    }

    actual fun success() {
        UINotificationFeedbackGenerator().notificationOccurred(
            UINotificationFeedbackType.UINotificationFeedbackTypeSuccess,
        )
    }
}
